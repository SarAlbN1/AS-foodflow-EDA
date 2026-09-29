package com.foodflow.notification.application;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodflow.notification.domain.Notification;
import com.foodflow.notification.domain.ProcessedEvent;
import com.foodflow.notification.infrastructure.messaging.NotificationEventPublisher;
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
    private final NotificationEventPublisher publicador;

    public NotificationApplicationService(NotificationRepository notificaciones,
            ProcessedEventRepository procesados, NotificationEventPublisher publicador) {
        this.notificaciones = notificaciones;
        this.procesados = procesados;
        this.publicador = publicador;
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

    /**
     * Registra que la notificacion se envio y publica {@code NotificationSent} (HU-303).
     *
     * <p>Las dos cosas van en la <strong>misma transaccion</strong>, y el evento se publica
     * <strong>despues del commit</strong>: una notificacion que no llega a quedar {@code ENVIADA}
     * no produce evento. La ventana inversa —commit hecho y publicacion fallida— es la que ADR-08
     * acepta, y solo deja un {@code ERROR} en el registro.
     *
     * <p>La entidad se vuelve a leer de la base en vez de usar la que trae quien llama: esa viene
     * de la transaccion anterior y esta desligada, asi que escribir sobre ella no seria una
     * actualizacion sino una fusion.
     *
     * <p>Si la notificacion ya no esta en {@code PENDIENTE} no se toca y no se publica nada: la
     * maquina de estados de {@code comportamiento-del-flujo.md} ignora esa transicion con
     * {@code WARN}, sin error, para que una reentrega no produzca un segundo
     * {@code NotificationSent}.
     *
     * @return {@code true} si la notificacion paso a {@code ENVIADA}
     */
    @Transactional
    public boolean registrarEnvio(UUID notificationId, String providerReference, int attempts,
            UUID correlationId) {
        Notification notificacion = notificaciones.findById(notificationId).orElse(null);
        if (notificacion == null) {
            log.warn("No existe la notificacion que se acaba de enviar notificationId={} correlationId={}",
                    notificationId, correlationId);
            return false;
        }
        if (!notificacion.marcarEnviada(attempts)) {
            log.warn("La notificacion no estaba PENDIENTE, no se registra el envio notificationId={} "
                            + "status={} correlationId={}",
                    notificationId, notificacion.status(), correlationId);
            return false;
        }

        notificaciones.saveAndFlush(notificacion);
        publicador.publicarEnviada(notificacion, providerReference, correlationId);

        log.info("Notificacion enviada notificationId={} orderId={} intentos={} providerReference={} "
                        + "correlationId={}",
                notificacion.id(), notificacion.orderId(), notificacion.attempts(),
                providerReference, correlationId);
        return true;
    }
}
