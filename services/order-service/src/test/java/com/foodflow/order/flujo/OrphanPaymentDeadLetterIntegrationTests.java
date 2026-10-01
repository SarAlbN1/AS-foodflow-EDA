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
 * Un resultado de pago cuyo pedido no existe en Order DB va a {@code payments.events.dlq}
 * <strong>sin reintentar</strong> (correccion de HU-602 medida en HU-608).
 *
 * <p><strong>Como se demuestra que no hubo reintentos sin depender de un cronometro fino.</strong>
 * La prueba sube la espera inicial a {@value #ESPERA_INICIAL_REINTENTO}: con la politica anterior
 * el evento habria tardado 10 s + 20 s en llegar a la DLQ. Si aparece en menos de 10 s, no se
 * reintento. El margen es de decenas de segundos, no de milisegundos.
 *
 * <p>Se omite sin las variables de entorno, igual que las demas pruebas de flujo:
 *
 * <pre>
 * set -a &amp;&amp; . ./.env &amp;&amp; set +a
 * export KAFKA_BOOTSTRAP_SERVERS="localhost:${KAFKA_HOST_PORT:-29092}"
 * export ORDER_DB_URL="jdbc:postgresql://localhost:${ORDER_DB_HOST_PORT:-5433}/${ORDER_DB_NAME:-orderdb}"
 * cd services/order-service &amp;&amp; ./mvnw verify
 * </pre>
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=true")
@EnabledIfEnvironmentVariable(named = "KAFKA_BOOTSTRAP_SERVERS", matches = ".+")
@EnabledIfEnvironmentVariable(named = "ORDER_DB_URL", matches = ".+")
class OrphanPaymentDeadLetterIntegrationTests {

    /** Espera entre reintentos durante la prueba; la real la fija el {@code .env}. */
    static final String ESPERA_INICIAL_REINTENTO = "10s";

    /** Si el evento llega antes de esto, no puede haber pasado por la primera espera. */
    private static final Duration SIN_REINTENTOS = Duration.ofSeconds(9);

    private static final Duration ESPERA = Duration.ofSeconds(40);
    private static final Duration SONDEO = Duration.ofMillis(500);

    /** Las dos publicaciones van a la misma particion: es donde importa que no se atasque. */
    private static final int PARTICION = 0;

    @DynamicPropertySource
    static void grupoAislado(DynamicPropertyRegistry registro) {
        registro.add("foodflow.kafka.payments-consumer-group", () -> "hu602-order-" + UUID.randomUUID());
        registro.add("spring.kafka.consumer.auto-offset-reset", () -> "latest");
        registro.add("foodflow.kafka.retry.initial-interval", () -> ESPERA_INICIAL_REINTENTO);
    }

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private KafkaListenerEndpointRegistry contenedores;

    @Autowired
    private OrderRepository pedidos;

    @Value("${foodflow.kafka.payments-topic}")
    private String paymentsTopic;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    @DisplayName("criterio 1: el pago de un pedido inexistente llega a la DLQ sin reintentos")
    void elPagoHuerfanoVaALaDlqSinReintentar() throws Exception {
        UUID huerfano = UUID.randomUUID();

        try (KafkaConsumer<String, String> testigo = testigoDe(paymentsTopic + ".dlq")) {
            esperarAsignacionDeParticiones();

            Instant publicado = Instant.now();
            kafka.send(paymentsTopic, PARTICION, huerfano.toString(),
                    eventoDePago("PaymentApproved", huerfano)).get();

            String enLaDlq = esperarEventoCon(testigo, huerfano.toString());
            Duration tardanza = Duration.between(publicado, Instant.now());

            assertThat(enLaDlq).contains("\"eventType\":\"PaymentApproved\"");
            assertThat(tardanza)
                    .as("con reintentos habria tardado al menos 30 s; tardo %s", tardanza)
                    .isLessThan(SIN_REINTENTOS);
            assertThat(pedidos.findById(huerfano)).isEmpty();
        }
    }

    @Test
    @DisplayName("criterio 2: el evento bueno que va detras del huerfano se procesa igual")
    void elEventoBuenoDetrasNoSeAtasca() throws Exception {
        UUID huerfano = UUID.randomUUID();
        Order pedido = pedidos.saveAndFlush(Order.crear("PED-HU602", NotificationChannel.EMAIL,
                "ana@foodflow.test", PaymentToken.PAY_OK, new BigDecimal("45900.00")));

        try {
            esperarAsignacionDeParticiones();

            kafka.send(paymentsTopic, PARTICION, huerfano.toString(),
                    eventoDePago("PaymentApproved", huerfano)).get();
            kafka.send(paymentsTopic, PARTICION, pedido.id().toString(),
                    eventoDePago("PaymentApproved", pedido.id())).get();

            assertThat(esperarEstado(pedido.id(), OrderStatus.PAGADO)).isEqualTo(OrderStatus.PAGADO);
        } finally {
            pedidos.deleteById(pedido.id());
        }
    }

    /** Envelope y payload tal como los publica Payment Service (HU-203). */
    private static String eventoDePago(String tipo, UUID orderId) {
        return """
                {"eventId":"%s","eventType":"%s","eventVersion":1,"occurredAt":"%s",\
                "correlationId":"%s","aggregateId":"%s",\
                "payload":{"paymentId":"%s","orderId":"%s","amount":45900.00,"currency":"COP",\
                "transactionReference":"TXN-HU602",\
                "notificationContact":{"channel":"EMAIL","destination":"ana@foodflow.test"}}}"""
                .formatted(UUID.randomUUID(), tipo, Instant.now(), UUID.randomUUID(), orderId,
                        UUID.randomUUID(), orderId);
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
                if (registro.value().contains(aguja)) {
                    return registro.value();
                }
            }
        }
        throw new AssertionError("no aparecio en la DLQ un evento de " + aguja);
    }

    private KafkaConsumer<String, String> testigoDe(String topico) {
        Properties propiedades = new Properties();
        propiedades.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propiedades.put(ConsumerConfig.GROUP_ID_CONFIG, "hu602-testigo-" + UUID.randomUUID());
        propiedades.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        KafkaConsumer<String, String> consumidor =
                new KafkaConsumer<>(propiedades, new StringDeserializer(), new StringDeserializer());
        TopicPartition particion = new TopicPartition(topico, PARTICION);
        consumidor.assign(List.of(particion));
        consumidor.seekToEnd(List.of(particion));
        consumidor.position(particion);
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
