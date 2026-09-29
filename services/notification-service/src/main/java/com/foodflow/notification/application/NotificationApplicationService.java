package com.foodflow.notification.application;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodflow.notification.domain.Notification;
import com.foodflow.notification.domain.ProcessedEvent;
import com.foodflow.notification.infrastructure.persistence.NotificationRepository;
import com.foodflow.notification.infrastructure.persistence.ProcessedEventRepository;

/**
 * Caso de uso de notificacion. Recibe ordenes ya validadas desde
 * {@code infrastructure.messaging} y no conoce Kafka ni JSON.
 *
 * <p>Crea la notificacion en {@code PENDIENTE} y no la envia: el envio al proveedor es HU-302 y
 * el registro de su resultado, HU-303 y HU-304.
 *
 * <p><strong>Alcance de HU-301.</strong> Aqui se implementa la idempotencia de este consumidor
 * (ADR-09), que el criterio 4 exige. HU-601 extiende lo mismo a los demas servicios y anade las
 * pruebas que entregan dos veces cada evento.
 */
@Service
public class NotificationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationApplicationService.class);

    /** Identifica a este consumidor en {@code processed_events}. */
    static final String CONSUMIDOR = "notification-service.payments";

    private final NotificationRepository notificaciones;
    private final ProcessedEventRepository procesados;

    public NotificationApplicationService(NotificationRepository notificaciones,
            ProcessedEventRepository procesados) {
        this.notificaciones = notificaciones;
        this.procesados = procesados;
    }

    /**
     * Crea la notificacion pendiente del resultado de un pago.
     *
     * <p><strong>Un evento, una notificacion.</strong> El {@code eventId} se registra en
     * {@code processed_events} en la <strong>misma transaccion local</strong> que la
     * notificacion (ADR-09): o quedan las dos cosas o no queda ninguna. Si el evento ya estaba
     * registrado se ignora con {@code INFO} y no se crea nada, de modo que una reentrega de
     * Kafka no produce un segundo correo.
     *
     * <p>El contacto es dato personal y se registra enmascarado, con la misma regla con la que
     * el contrato REST lo devuelve ({@code convenciones.md}).
     *
     * @return la notificacion creada, o vacio si el evento ya se habia procesado
     */
    @Transactional
    public Optional<Notification> notificarResultado(NotifyPaymentResultCommand orden) {
        if (procesados.existsById(orden.eventId())) {
            log.info("Evento ya procesado, no se crea otra notificacion. eventId={} orderId={} correlationId={}",
                    orden.eventId(), orden.orderId(), orden.correlationId());
            return Optional.empty();
        }

        String texto = orden.aprobado()
                ? NotificationContent.aprobado(orden.amount(), orden.currency())
                : NotificationContent.rechazado(orden.amount(), orden.currency());

        Notification notificacion = notificaciones.saveAndFlush(Notification.pendiente(
                orden.orderId(), orden.paymentId(), orden.channel(), orden.destination(), texto));

        // En la misma transaccion: si esto falla, la notificacion no queda.
        procesados.saveAndFlush(ProcessedEvent.de(orden.eventId(), CONSUMIDOR));

        log.info("Notificacion creada notificationId={} orderId={} paymentId={} status={} canal={} "
                        + "eventId={} correlationId={} contacto={}",
                notificacion.id(), notificacion.orderId(), notificacion.paymentId(),
                notificacion.status(), notificacion.channel(), orden.eventId(),
                orden.correlationId(), ContactMasker.mask(notificacion.destination()));

        return Optional.of(notificacion);
    }
}
