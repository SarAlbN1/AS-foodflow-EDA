package com.foodflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.foodflow.notification.application.NotificationApplicationService;
import com.foodflow.notification.application.NotifyPaymentResultCommand;
import com.foodflow.notification.domain.Notification;
import com.foodflow.notification.domain.NotificationChannel;
import com.foodflow.notification.domain.NotificationStatus;
import com.foodflow.notification.infrastructure.persistence.NotificationRepository;
import com.foodflow.notification.infrastructure.persistence.ProcessedEventRepository;

/**
 * Arranque del contexto completo y persistencia real en Notification DB (criterios 2, 3 y 4 de
 * HU-301). Comprueba ademas que el mapeo JPA coincide con
 * {@code infrastructure/postgres/notification-db/01-schema.sql}, porque el contexto solo arranca
 * si {@code ddl-auto=validate} lo acepta.
 *
 * <p>Necesita la base levantada ({@code docker compose -f infrastructure/compose/docker-compose.yml
 * up -d notification-db}) y las variables de {@code .env}. Se omite cuando
 * {@code NOTIFICATION_DB_URL} no esta definida, para que {@code ./mvnw verify} siga funcionando
 * sin infraestructura; el prototipo no usa Testcontainers (es opcional, HU-011).
 *
 * <p>El consumidor de Kafka se deja parado: lo que se prueba aqui es la escritura, y el consumo
 * del evento ya esta cubierto por {@code PaymentResultEventConsumerTests} sin broker.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@EnabledIfEnvironmentVariable(named = "NOTIFICATION_DB_URL", matches = ".+")
class NotificationServiceApplicationTests {

    @Autowired
    private NotificationApplicationService servicio;

    @Autowired
    private NotificationRepository notificaciones;

    @Autowired
    private ProcessedEventRepository procesados;

    @Test
    void contextLoads() {
        assertThat(servicio).isNotNull();
    }

    @Test
    @DisplayName("criterios 2 y 3: un pago aprobado deja una notificacion PENDIENTE con el canal y el destino del evento")
    void creaLaNotificacionPendiente() {
        UUID orderId = UUID.randomUUID();
        NotifyPaymentResultCommand orden = resultado(orderId, true);

        Notification creada = servicio.notificarResultado(orden).orElseThrow();

        try {
            Notification guardada = notificaciones.findById(creada.id()).orElseThrow();

            assertThat(guardada.orderId()).isEqualTo(orderId);
            assertThat(guardada.paymentId()).isEqualTo(orden.paymentId());
            assertThat(guardada.status()).isEqualTo(NotificationStatus.PENDIENTE);
            assertThat(guardada.channel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(guardada.destination()).isEqualTo("ana@foodflow.test");
            assertThat(guardada.content()).contains("aprobado");
            assertThat(guardada.attempts()).isZero();
            assertThat(guardada.failureCode()).isNull();
            assertThat(guardada.createdAt()).isNotNull();
        } finally {
            limpiar(creada, orden);
        }
    }

    @Test
    @DisplayName("criterio 3: un pago rechazado deja la notificacion del rechazo, tambien PENDIENTE")
    void creaLaNotificacionDelRechazo() {
        NotifyPaymentResultCommand orden = resultado(UUID.randomUUID(), false);

        Notification creada = servicio.notificarResultado(orden).orElseThrow();

        try {
            Notification guardada = notificaciones.findById(creada.id()).orElseThrow();

            assertThat(guardada.status()).isEqualTo(NotificationStatus.PENDIENTE);
            assertThat(guardada.content()).contains("rechazado");
        } finally {
            limpiar(creada, orden);
        }
    }

    @Test
    @DisplayName("criterio 4: reentregar el mismo eventId no crea una segunda notificacion")
    void laReentregaNoDuplica() {
        UUID orderId = UUID.randomUUID();
        NotifyPaymentResultCommand orden = resultado(orderId, true);

        Notification creada = servicio.notificarResultado(orden).orElseThrow();
        Optional<Notification> repetida = servicio.notificarResultado(orden);

        try {
            assertThat(repetida).isEmpty();

            List<Notification> delPedido = notificaciones.findAll().stream()
                    .filter(n -> n.orderId().equals(orderId))
                    .toList();
            assertThat(delPedido).hasSize(1);
            assertThat(procesados.findById(orden.eventId())).isPresent();
        } finally {
            limpiar(creada, orden);
        }
    }

    private NotifyPaymentResultCommand resultado(UUID orderId, boolean aprobado) {
        return new NotifyPaymentResultCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                orderId,
                UUID.randomUUID(),
                new BigDecimal("45900.00"),
                "COP",
                aprobado,
                NotificationChannel.EMAIL,
                "ana@foodflow.test");
    }

    private void limpiar(Notification creada, NotifyPaymentResultCommand orden) {
        notificaciones.deleteById(creada.id());
        procesados.deleteById(orden.eventId());
    }
}
