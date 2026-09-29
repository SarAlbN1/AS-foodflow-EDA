package com.foodflow.notification.flujo;

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

import com.foodflow.notification.domain.Notification;
import com.foodflow.notification.domain.NotificationStatus;
import com.foodflow.notification.infrastructure.persistence.NotificationRepository;

/**
 * HU-605, criterio 3: {@code PaymentApproved} y {@code PaymentRejected} llegan a Notification
 * Service <strong>por Kafka de verdad</strong>.
 *
 * <p>El criterio 4 es lo que da forma a esta prueba: <em>verifican persistencia y evento
 * resultante, no solo que el metodo haya sido invocado</em>. Aqui no hay ningun doble. Se publica
 * el evento de pago en {@code payments.events} con un productor real, lo consume el
 * {@code @KafkaListener} del propio servicio, y se comprueban las dos salidas: la fila en
 * Notification DB y el {@code NotificationSent} que aparece en {@code notifications.events}.
 *
 * <p><strong>Grupo de consumidores propio y desde el final.</strong> La prueba usa un
 * {@code group-id} unico y {@code auto-offset-reset=latest} para no reprocesar el historico del
 * topico: un grupo nuevo desde el principio volveria a notificar todos los pagos anteriores, y
 * los enviaria otra vez al proveedor. Por eso tambien se espera a que el contenedor tenga
 * particiones asignadas <strong>antes</strong> de publicar; si no, el evento saldria antes de que
 * hubiera nadie escuchando.
 *
 * <p>Entorno reproducible (criterio 5), con el de {@code scripts/up.sh} o el de Compose:
 *
 * <pre>
 * set -a &amp;&amp; . ./.env &amp;&amp; set +a
 * export KAFKA_BOOTSTRAP_SERVERS="localhost:${KAFKA_HOST_PORT:-29092}"
 * export NOTIFICATION_DB_URL="jdbc:postgresql://localhost:${NOTIFICATION_DB_HOST_PORT:-5435}/${NOTIFICATION_DB_NAME:-notificationdb}"
 * export NOTIFICATION_PROVIDER_URL="http://localhost:${NOTIFICATION_PROVIDER_HOST_PORT:-8090}"
 * cd services/notification-service &amp;&amp; ./mvnw verify
 * </pre>
 *
 * <p>Se omite si falta cualquiera de las dos variables, para que {@code ./mvnw verify} siga
 * corriendo sin infraestructura. No usa Testcontainers, que es opcional (HU-011).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "KAFKA_BOOTSTRAP_SERVERS", matches = ".+")
@EnabledIfEnvironmentVariable(named = "NOTIFICATION_DB_URL", matches = ".+")
class PaymentResultFlowIntegrationTests {

    private static final Duration ESPERA = Duration.ofSeconds(30);
    private static final Duration SONDEO = Duration.ofMillis(500);

    /**
     * Grupo propio de cada ejecucion: no toca los offsets del servicio real ni compite con el por
     * las particiones si esta levantado.
     */
    @DynamicPropertySource
    static void grupoAislado(DynamicPropertyRegistry registro) {
        registro.add("spring.kafka.consumer.group-id", () -> "hu605-notification-" + UUID.randomUUID());
        registro.add("spring.kafka.consumer.auto-offset-reset", () -> "latest");
    }

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private KafkaListenerEndpointRegistry contenedores;

    @Autowired
    private NotificationRepository notificaciones;

    @Value("${foodflow.kafka.payments-topic}")
    private String paymentsTopic;

    @Value("${foodflow.kafka.notifications-topic}")
    private String notificationsTopic;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    @DisplayName("criterios 3 y 4: un PaymentApproved real crea la notificacion y produce NotificationSent")
    void elPagoAprobadoRecorreElFlujoCompleto() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        try (KafkaConsumer<String, String> testigo = testigoDe(notificationsTopic)) {
            esperarAsignacionDeParticiones();

            kafka.send(paymentsTopic, orderId.toString(),
                    paymentApproved(orderId, paymentId, UUID.randomUUID())).get();

            // 1) Persistencia: la notificacion existe y quedo resuelta.
            Notification notificacion = esperarNotificacionDe(orderId);
            assertThat(notificacion.orderId()).isEqualTo(orderId);
            assertThat(notificacion.paymentId()).isEqualTo(paymentId);
            assertThat(notificacion.content()).contains("aprobado");
            assertThat(notificacion.status()).isEqualTo(NotificationStatus.ENVIADA);
            assertThat(notificacion.attempts()).isPositive();

            // 2) Evento resultante: no basta con que el metodo se llamara.
            String evento = esperarEventoCon(testigo, notificacion.id().toString());
            assertThat(evento)
                    .contains("\"eventType\":\"NotificationSent\"")
                    .contains("\"orderId\":\"" + orderId + "\"")
                    .contains("\"paymentId\":\"" + paymentId + "\"");
        }
    }

    @Test
    @DisplayName("criterio 3: el mismo evento entregado dos veces produce una sola notificacion")
    void laReentregaNoDuplicaEnElFlujoReal() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String evento = paymentApproved(orderId, UUID.randomUUID(), eventId);

        esperarAsignacionDeParticiones();

        // El mismo eventId dos veces, como lo haria una reentrega del broker (ADR-09).
        kafka.send(paymentsTopic, orderId.toString(), evento).get();
        esperarNotificacionDe(orderId);
        kafka.send(paymentsTopic, orderId.toString(), evento).get();

        // Se espera un tiempo prudencial: lo que se afirma es que NO aparece una segunda fila.
        Thread.sleep(3000);

        assertThat(notificaciones.findAll().stream().filter(n -> n.orderId().equals(orderId)))
                .hasSize(1);
    }

    /** Envelope y payload tal como los publica Payment Service (HU-203). */
    private static String paymentApproved(UUID orderId, UUID paymentId, UUID eventId) {
        return """
                {"eventId":"%s","eventType":"PaymentApproved","eventVersion":1,\
                "occurredAt":"%s","correlationId":"%s","aggregateId":"%s",\
                "payload":{"paymentId":"%s","orderId":"%s","amount":45900.00,"currency":"COP",\
                "transactionReference":"TXN-HU605","notificationContact":{"channel":"EMAIL",\
                "destination":"ana@foodflow.test"}}}"""
                .formatted(eventId, Instant.now(), UUID.randomUUID(), orderId, paymentId, orderId);
    }

    /**
     * Sin particiones asignadas el evento se publicaria sin que nadie lo escuche, y la prueba
     * fallaria por una carrera y no por el flujo.
     */
    private void esperarAsignacionDeParticiones() {
        Instant limite = Instant.now().plus(ESPERA);
        while (Instant.now().isBefore(limite)) {
            boolean asignados = contenedores.getListenerContainers().stream()
                    .map(MessageListenerContainer::getAssignedPartitions)
                    .allMatch(p -> p != null && !p.isEmpty());
            if (asignados && !contenedores.getListenerContainers().isEmpty()) {
                return;
            }
            dormir();
        }
        throw new IllegalStateException("el consumidor no recibio particiones en " + ESPERA);
    }

    private Notification esperarNotificacionDe(UUID orderId) {
        Instant limite = Instant.now().plus(ESPERA);
        while (Instant.now().isBefore(limite)) {
            Optional<Notification> encontrada = notificaciones.findAll().stream()
                    .filter(n -> n.orderId().equals(orderId))
                    .findFirst();
            if (encontrada.isPresent() && encontrada.get().status() != NotificationStatus.PENDIENTE) {
                return encontrada.get();
            }
            dormir();
        }
        throw new AssertionError("no llego la notificacion resuelta del pedido " + orderId);
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
        throw new AssertionError("no aparecio en " + notificationsTopic + " ningun evento con " + aguja);
    }

    /** Consumidor de prueba posicionado al final: solo ve lo que se publique a partir de ahora. */
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
        // Fuerza la resolucion de las posiciones antes de que la prueba publique nada.
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
