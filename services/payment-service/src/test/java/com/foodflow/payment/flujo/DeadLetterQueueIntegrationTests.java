package com.foodflow.payment.flujo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

import com.foodflow.payment.infrastructure.persistence.PaymentRepository;

/**
 * HU-602 contra Kafka real: un evento no procesable termina en {@code orders.events.dlq} y el
 * resto de la particion sigue procesandose.
 *
 * <p>Los criterios 3 y 4 solo se pueden comprobar asi. Un doble diria que se llamo al
 * {@code DeadLetterPublishingRecoverer}; lo que hay que saber es que <strong>el mensaje esta en la
 * DLQ</strong> y que el evento bueno que va detras no se quedo atascado.
 *
 * <p>Se omite sin las variables de entorno, igual que las demas pruebas de flujo.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "KAFKA_BOOTSTRAP_SERVERS", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PAYMENT_DB_URL", matches = ".+")
class DeadLetterQueueIntegrationTests {

    private static final Duration ESPERA = Duration.ofSeconds(40);
    private static final Duration SONDEO = Duration.ofMillis(500);

    @DynamicPropertySource
    static void grupoAislado(DynamicPropertyRegistry registro) {
        registro.add("spring.kafka.consumer.group-id", () -> "hu602-payment-" + UUID.randomUUID());
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

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    @DisplayName("criterios 3 y 4: el evento no procesable acaba en la DLQ y el siguiente se procesa")
    void elEventoNoProcesableVaALaDlqYElRestoSigue() throws Exception {
        UUID orderIdRoto = UUID.randomUUID();
        UUID orderIdBueno = UUID.randomUUID();

        try (KafkaConsumer<String, String> dlq = testigoDe(ordersTopic + ".dlq")) {
            esperarAsignacionDeParticiones();

            // eventVersion 99: fuera de contrato, no mejora por reintentarlo.
            kafka.send(ordersTopic, orderIdRoto.toString(), envelopeConVersion(orderIdRoto, 99)).get();
            // Detras, uno bueno en la misma particion que el roto no debe bloquear.
            kafka.send(ordersTopic, orderIdBueno.toString(), envelopeConVersion(orderIdBueno, 1)).get();

            String enviadoALaDlq = esperarEnLaDlq(dlq, orderIdRoto.toString());
            assertThat(enviadoALaDlq).contains("\"eventVersion\":99");

            // Criterio 4: el resto continua.
            assertThat(esperarPago(orderIdBueno)).isTrue();
            // Y el roto no se cobro.
            assertThat(pagos.findByOrderId(orderIdRoto)).isEmpty();

            pagos.findByOrderId(orderIdBueno).ifPresent(p -> pagos.deleteById(p.id()));
        }
    }

    private static String envelopeConVersion(UUID orderId, int version) {
        return """
                {"eventId":"%s","eventType":"OrderCreated","eventVersion":%d,"occurredAt":"%s",\
                "correlationId":"%s","aggregateId":"%s",\
                "payload":{"orderId":"%s","customerReference":"PED-HU602","total":45900.00,\
                "currency":"COP","paymentToken":"PAY-OK",\
                "notificationContact":{"channel":"EMAIL","destination":"ana@foodflow.test"}}}"""
                .formatted(UUID.randomUUID(), version, Instant.now(), UUID.randomUUID(), orderId, orderId);
    }

    private void esperarAsignacionDeParticiones() {
        Instant limite = Instant.now().plus(ESPERA);
        while (Instant.now().isBefore(limite)) {
            List<MessageListenerContainer> lista = List.copyOf(contenedores.getListenerContainers());
            if (!lista.isEmpty() && lista.stream()
                    .allMatch(c -> c.getAssignedPartitions() != null && !c.getAssignedPartitions().isEmpty())) {
                return;
            }
            dormir();
        }
        throw new IllegalStateException("el consumidor no recibio particiones en " + ESPERA);
    }

    private boolean esperarPago(UUID orderId) {
        Instant limite = Instant.now().plus(ESPERA);
        while (Instant.now().isBefore(limite)) {
            if (pagos.findByOrderId(orderId).isPresent()) {
                return true;
            }
            dormir();
        }
        return false;
    }

    private String esperarEnLaDlq(KafkaConsumer<String, String> dlq, String aguja) {
        Instant limite = Instant.now().plus(ESPERA);
        while (Instant.now().isBefore(limite)) {
            ConsumerRecords<String, String> lote = dlq.poll(SONDEO);
            for (ConsumerRecord<String, String> registro : lote) {
                if (registro.value() != null && registro.value().contains(aguja)) {
                    return registro.value();
                }
            }
        }
        throw new AssertionError("el evento de " + aguja + " no llego a " + ordersTopic + ".dlq");
    }

    private KafkaConsumer<String, String> testigoDe(String topico) {
        Properties propiedades = new Properties();
        propiedades.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propiedades.put(ConsumerConfig.GROUP_ID_CONFIG, "hu602-testigo-" + UUID.randomUUID());
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
