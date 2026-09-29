package com.foodflow.payment.flujo;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.foodflow.payment.domain.Payment;
import com.foodflow.payment.domain.PaymentStatus;
import com.foodflow.payment.domain.RejectionReason;
import com.foodflow.payment.infrastructure.persistence.PaymentRepository;

/**
 * HU-605, criterio 1: {@code OrderCreated} llega a Payment Service <strong>por Kafka de
 * verdad</strong>.
 *
 * <p>El criterio 4 se cumple comprobando las dos salidas: el pago en Payment DB y el evento de
 * resultado que aparece en {@code payments.events}. Que el metodo se haya invocado no basta.
 *
 * <p>Se prueban los dos desenlaces de ADR-10, porque son ramas distintas del flujo:
 * {@code PAY-OK} produce {@code PaymentApproved} y {@code PAY-FAIL} produce
 * {@code PaymentRejected}.
 *
 * <p>Es una copia del patron que usan order-service y notification-service para sus propios
 * criterios, no una clase compartida: ningun servicio depende de codigo de otro (regla 8).
 *
 * <p>Grupo unico y {@code auto-offset-reset=latest}: un grupo nuevo desde el principio
 * reprocesaria todo {@code orders.events} y cobraria de nuevo pedidos antiguos. Se espera a la
 * asignacion de particiones antes de publicar.
 *
 * <p>Entorno reproducible (criterio 5):
 *
 * <pre>
 * set -a &amp;&amp; . ./.env &amp;&amp; set +a
 * export KAFKA_BOOTSTRAP_SERVERS="localhost:${KAFKA_HOST_PORT:-29092}"
 * export PAYMENT_DB_URL="jdbc:postgresql://localhost:${PAYMENT_DB_HOST_PORT:-5434}/${PAYMENT_DB_NAME:-paymentdb}"
 * cd services/payment-service &amp;&amp; ./mvnw verify
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "KAFKA_BOOTSTRAP_SERVERS", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PAYMENT_DB_URL", matches = ".+")
class OrderCreatedFlowIntegrationTests {

    private static final Duration ESPERA = Duration.ofSeconds(30);
    private static final Duration SONDEO = Duration.ofMillis(500);

    @DynamicPropertySource
    static void grupoAislado(DynamicPropertyRegistry registro) {
        registro.add("spring.kafka.consumer.group-id", () -> "hu605-payment-" + UUID.randomUUID());
        registro.add("spring.kafka.consumer.auto-offset-reset", () -> "latest");
    }

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private KafkaListenerEndpointRegistry contenedores;

    @Autowired
    private PaymentRepository pagos;

    @Value("${foodflow.kafka.orders-topic}")
    private String ordersTopic;

    @Value("${foodflow.kafka.payments-topic}")
    private String paymentsTopic;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    @DisplayName("criterios 1 y 4: PAY-OK persiste el pago APROBADO y produce PaymentApproved")
    void elPedidoConPayOkSeCobraYPublica() throws Exception {
        UUID orderId = UUID.randomUUID();

        try (KafkaConsumer<String, String> testigo = testigoDe(paymentsTopic)) {
            esperarAsignacionDeParticiones();

            kafka.send(ordersTopic, orderId.toString(), orderCreated(orderId, "PAY-OK")).get();

            Payment pago = esperarPagoDe(orderId);
            try {
                assertThat(pago.status()).isEqualTo(PaymentStatus.APROBADO);
                assertThat(pago.reasonCode()).isNull();
                assertThat(pago.transactionReference()).isNotBlank();

                assertThat(esperarEventoCon(testigo, orderId.toString()))
                        .contains("\"eventType\":\"PaymentApproved\"")
                        .contains("\"transactionReference\"");
            } finally {
                pagos.deleteById(pago.id());
            }
        }
    }

    @Test
    @DisplayName("criterio 1: PAY-FAIL persiste el pago RECHAZADO y produce PaymentRejected")
    void elPedidoConPayFailSeRechazaYPublica() throws Exception {
        UUID orderId = UUID.randomUUID();

        try (KafkaConsumer<String, String> testigo = testigoDe(paymentsTopic)) {
            esperarAsignacionDeParticiones();

            kafka.send(ordersTopic, orderId.toString(), orderCreated(orderId, "PAY-FAIL")).get();

            Payment pago = esperarPagoDe(orderId);
            try {
                assertThat(pago.status()).isEqualTo(PaymentStatus.RECHAZADO);
                assertThat(pago.reasonCode()).isEqualTo(RejectionReason.PAGO_RECHAZADO_POR_TOKEN);

                assertThat(esperarEventoCon(testigo, orderId.toString()))
                        .contains("\"eventType\":\"PaymentRejected\"")
                        .contains("\"reasonCode\":\"PAGO_RECHAZADO_POR_TOKEN\"");
            } finally {
                pagos.deleteById(pago.id());
            }
        }
    }

    /** Envelope y payload tal como los publica Order Service (HU-103). */
    private static String orderCreated(UUID orderId, String paymentToken) {
        return """
                {"eventId":"%s","eventType":"OrderCreated","eventVersion":1,"occurredAt":"%s",\
                "correlationId":"%s","aggregateId":"%s",\
                "payload":{"orderId":"%s","customerReference":"PED-HU605","total":45900.00,\
                "currency":"COP","paymentToken":"%s",\
                "notificationContact":{"channel":"EMAIL","destination":"ana@foodflow.test"}}}"""
                .formatted(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), orderId, orderId,
                        paymentToken);
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

    private Payment esperarPagoDe(UUID orderId) {
        Instant limite = Instant.now().plus(ESPERA);
        while (Instant.now().isBefore(limite)) {
            Optional<Payment> encontrado = pagos.findByOrderId(orderId);
            if (encontrado.isPresent()) {
                return encontrado.get();
            }
            dormir();
        }
        throw new AssertionError("no se persistio el pago del pedido " + orderId);
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
        throw new AssertionError("no aparecio en " + paymentsTopic + " ningun evento de " + aguja);
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
