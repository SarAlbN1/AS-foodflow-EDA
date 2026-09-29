package com.foodflow.order.flujo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.foodflow.order.domain.NotificationChannel;
import com.foodflow.order.domain.Order;
import com.foodflow.order.domain.OrderStatus;
import com.foodflow.order.domain.PaymentToken;
import com.foodflow.order.infrastructure.persistence.OrderRepository;

/**
 * HU-605, criterio 2: {@code PaymentApproved} y {@code PaymentRejected} llegan a Order Service
 * <strong>por Kafka de verdad</strong>.
 *
 * <p>Lo que exige el criterio 4 —verificar persistencia y evento resultante, no que se haya
 * llamado a un metodo— se comprueba en las dos salidas: el estado del pedido en Order DB y el
 * {@code OrderStatusChanged} que aparece en {@code orders.events}.
 *
 * <p>Es una copia del patron que usa notification-service para su propio criterio, no una clase
 * compartida: ningun servicio depende de codigo de otro (regla arquitectonica 8).
 *
 * <p>Grupo de consumidores unico y {@code auto-offset-reset=latest}: un grupo nuevo desde el
 * principio reprocesaria todo el historico de {@code payments.events} y cambiaria el estado de
 * pedidos antiguos. Se espera a la asignacion de particiones antes de publicar, para que la
 * prueba no dependa de una carrera.
 *
 * <p>Entorno reproducible (criterio 5):
 *
 * <pre>
 * set -a &amp;&amp; . ./.env &amp;&amp; set +a
 * export KAFKA_BOOTSTRAP_SERVERS="localhost:${KAFKA_HOST_PORT:-29092}"
 * export ORDER_DB_URL="jdbc:postgresql://localhost:${ORDER_DB_HOST_PORT:-5433}/${ORDER_DB_NAME:-orderdb}"
 * cd services/order-service &amp;&amp; ./mvnw verify
 * </pre>
 */
// El resto de pruebas apaga el consumidor en src/test/resources/config/application.properties,
// para que un contexto de prueba no consuma eventos reales con el grupo real. Esta prueba lo
// vuelve a encender porque su objeto ES el consumo, y evita ese riesgo de la unica forma que de
// verdad lo evita: grupo propio de cada ejecucion y lectura desde el final del topico.
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=true")
@EnabledIfEnvironmentVariable(named = "KAFKA_BOOTSTRAP_SERVERS", matches = ".+")
@EnabledIfEnvironmentVariable(named = "ORDER_DB_URL", matches = ".+")
class PaymentResultFlowIntegrationTests {

    private static final Duration ESPERA = Duration.ofSeconds(30);
    private static final Duration SONDEO = Duration.ofMillis(500);

    @DynamicPropertySource
    static void grupoAislado(DynamicPropertyRegistry registro) {
        registro.add("foodflow.kafka.payments-consumer-group", () -> "hu605-order-" + UUID.randomUUID());
        registro.add("spring.kafka.consumer.auto-offset-reset", () -> "latest");
    }

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private KafkaListenerEndpointRegistry contenedores;

    @Autowired
    private OrderRepository pedidos;

    @Value("${foodflow.kafka.payments-topic}")
    private String paymentsTopic;

    @Value("${foodflow.kafka.orders-topic}")
    private String ordersTopic;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    @DisplayName("criterios 2 y 4: PaymentApproved deja el pedido PAGADO y produce OrderStatusChanged")
    void elPagoAprobadoActualizaElPedidoYPublica() throws Exception {
        Order pedido = pedidoCreado();

        try (KafkaConsumer<String, String> testigo = testigoDe(ordersTopic)) {
            esperarAsignacionDeParticiones();

            kafka.send(paymentsTopic, pedido.id().toString(),
                    eventoDePago("PaymentApproved", pedido.id(), "\"transactionReference\":\"TXN-HU605\"")).get();

            assertThat(esperarEstado(pedido.id(), OrderStatus.PAGADO)).isEqualTo(OrderStatus.PAGADO);

            String evento = esperarEventoCon(testigo, pedido.id().toString());
            assertThat(evento)
                    .contains("\"eventType\":\"OrderStatusChanged\"")
                    .contains("\"previousStatus\":\"CREADO\"")
                    .contains("\"newStatus\":\"PAGADO\"");
        } finally {
            pedidos.deleteById(pedido.id());
        }
    }

    @Test
    @DisplayName("criterio 2: PaymentRejected deja el pedido PAGO_RECHAZADO y produce su OrderStatusChanged")
    void elPagoRechazadoActualizaElPedidoYPublica() throws Exception {
        Order pedido = pedidoCreado();

        try (KafkaConsumer<String, String> testigo = testigoDe(ordersTopic)) {
            esperarAsignacionDeParticiones();

            kafka.send(paymentsTopic, pedido.id().toString(),
                    eventoDePago("PaymentRejected", pedido.id(),
                            "\"reasonCode\":\"PAGO_RECHAZADO_POR_TOKEN\"")).get();

            assertThat(esperarEstado(pedido.id(), OrderStatus.PAGO_RECHAZADO))
                    .isEqualTo(OrderStatus.PAGO_RECHAZADO);

            assertThat(esperarEventoCon(testigo, pedido.id().toString()))
                    .contains("\"newStatus\":\"PAGO_RECHAZADO\"");
        } finally {
            pedidos.deleteById(pedido.id());
        }
    }

    @Test
    @DisplayName("HU-601 criterios 4 y 5: el mismo evento entregado dos veces no reaplica el cambio")
    void laReentregaNoVuelveAAplicar() throws Exception {
        Order pedido = pedidoCreado();
        String evento = eventoDePago("PaymentApproved", pedido.id(),
                "\"transactionReference\":\"TXN-HU601\"");

        try {
            esperarAsignacionDeParticiones();

            kafka.send(paymentsTopic, pedido.id().toString(), evento).get();
            esperarEstado(pedido.id(), OrderStatus.PAGADO);
            Instant trasElPrimero = pedidos.findById(pedido.id()).orElseThrow().updatedAt();

            // El mismo eventId otra vez: la idempotencia de ADR-09 debe descartarlo.
            kafka.send(paymentsTopic, pedido.id().toString(), evento).get();
            Thread.sleep(3000);

            Order despues = pedidos.findById(pedido.id()).orElseThrow();
            assertThat(despues.status()).isEqualTo(OrderStatus.PAGADO);
            // Si se hubiera vuelto a aplicar, la marca de tiempo habria cambiado.
            assertThat(despues.updatedAt()).isEqualTo(trasElPrimero);
        } finally {
            pedidos.deleteById(pedido.id());
        }
    }

    private Order pedidoCreado() {
        Order pedido = Order.crear("PED-HU605", NotificationChannel.EMAIL, "ana@foodflow.test",
                PaymentToken.PAY_OK, new BigDecimal("45900.00"));
        return pedidos.saveAndFlush(pedido);
    }

    /** Envelope y payload tal como los publica Payment Service (HU-203 y HU-204). */
    private static String eventoDePago(String tipo, UUID orderId, String campoDistintivo) {
        return """
                {"eventId":"%s","eventType":"%s","eventVersion":1,"occurredAt":"%s",\
                "correlationId":"%s","aggregateId":"%s",\
                "payload":{"paymentId":"%s","orderId":"%s","amount":45900.00,"currency":"COP",%s,\
                "notificationContact":{"channel":"EMAIL","destination":"ana@foodflow.test"}}}"""
                .formatted(UUID.randomUUID(), tipo, Instant.now(), UUID.randomUUID(), orderId,
                        UUID.randomUUID(), orderId, campoDistintivo);
    }

    private void esperarAsignacionDeParticiones() {
        Instant limite = Instant.now().plus(ESPERA);
        while (Instant.now().isBefore(limite)) {
            List<MessageListenerContainer> lista = List.copyOf(contenedores.getListenerContainers());
            boolean asignados = !lista.isEmpty() && lista.stream()
                    .allMatch(c -> c.getAssignedPartitions() != null && !c.getAssignedPartitions().isEmpty());
            if (asignados) {
                return;
            }
            dormir();
        }
        throw new IllegalStateException("el consumidor no recibio particiones en " + ESPERA);
    }

    private OrderStatus esperarEstado(UUID orderId, OrderStatus esperado) {
        Instant limite = Instant.now().plus(ESPERA);
        while (Instant.now().isBefore(limite)) {
            Optional<Order> encontrado = pedidos.findById(orderId);
            if (encontrado.isPresent() && encontrado.get().status() == esperado) {
                return encontrado.get().status();
            }
            dormir();
        }
        throw new AssertionError("el pedido " + orderId + " no llego a " + esperado);
    }

    private String esperarEventoCon(KafkaConsumer<String, String> testigo, String aguja) {
        Instant limite = Instant.now().plus(ESPERA);
        while (Instant.now().isBefore(limite)) {
            ConsumerRecords<String, String> lote = testigo.poll(SONDEO);
            for (ConsumerRecord<String, String> registro : lote) {
                if (registro.value().contains(aguja) && registro.value().contains("OrderStatusChanged")) {
                    return registro.value();
                }
            }
        }
        throw new AssertionError("no aparecio en " + ordersTopic + " un OrderStatusChanged de " + aguja);
    }

    private KafkaConsumer<String, String> testigoDe(String topico) {
        Properties propiedades = new Properties();
        propiedades.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propiedades.put(ConsumerConfig.GROUP_ID_CONFIG, "hu605-testigo-" + UUID.randomUUID());
        propiedades.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        KafkaConsumer<String, String> consumidor =
                new KafkaConsumer<>(propiedades, new StringDeserializer(), new StringDeserializer());
        List<TopicPartition> particiones = consumidor.partitionsFor(topico).stream()
                .map(PartitionInfo::partition)
                .map(p -> new TopicPartition(topico, p))
                .toList();
        consumidor.assign(particiones);
        consumidor.seekToEnd(particiones);
        particiones.forEach(consumidor::position);
        return consumidor;
    }

    private static void dormir() {
        try {
            Thread.sleep(SONDEO);
        } catch (InterruptedException interrumpida) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrumpida);
        }
    }
}
