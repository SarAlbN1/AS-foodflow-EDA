package com.foodflow.notification.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.foodflow.notification.config.EventJsonConfig;
import com.foodflow.notification.domain.Notification;

import tools.jackson.databind.ObjectMapper;

/**
 * Publica los hechos de la notificacion en {@code notifications.events}: {@code NotificationSent}
 * (HU-303) y, cuando exista, {@code NotificationFailed} (HU-304).
 *
 * <p>Nadie del prototipo consume este topico: esta para observabilidad y consumidores futuros
 * ({@code docs/wiki/03-contratos/eventos.md}). Notification Service no llama por REST a nadie
 * para contar lo que hizo (reglas 4 y 7).
 *
 * <p><strong>Despues del commit (ADR-08).</strong> La publicacion se registra para ejecutarse
 * cuando la transaccion que persiste el cambio de estado ya hizo commit, asi que una notificacion
 * que no llega a guardarse nunca produce evento. La contrapartida es la misma ventana de
 * escritura dual que ADR-08 acepta: si el commit sale bien y la publicacion falla, la
 * notificacion queda {@code ENVIADA} sin que nadie se entere. Se registra el fallo y ahi termina:
 * no hay Outbox ni reconciliacion.
 *
 * <p><strong>Un fallo al publicar no revierte nada.</strong> El envio al cliente ya ocurrio y el
 * resultado del pago sigue registrado en Order Service (regla 12): hacer fallar el consumidor
 * aqui reentregaria el evento de pago y no arreglaria nada.
 */
@Component
public class NotificationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventPublisher.class);

    /** Nombre del evento en el catalogo de {@code docs/wiki/03-contratos/eventos.md}. */
    static final String NOTIFICATION_SENT = "NotificationSent";

    static final String NOTIFICATION_FAILED = "NotificationFailed";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper jackson;
    private final String notificationsTopic;

    public NotificationEventPublisher(
            KafkaTemplate<String, String> kafka,
            @Qualifier(EventJsonConfig.EVENT_OBJECT_MAPPER) ObjectMapper jackson,
            @Value("${foodflow.kafka.notifications-topic}") String notificationsTopic) {
        this.kafka = kafka;
        this.jackson = jackson;
        this.notificationsTopic = notificationsTopic;
    }

    /**
     * Publica que la notificacion se envio.
     *
     * @param providerReference referencia devuelta por el proveedor, nunca vacia
     * @param correlationId     correlacion del evento de pago que origino la notificacion
     */
    public void publicarEnviada(Notification notificacion, String providerReference,
            java.util.UUID correlationId) {
        publicar(EventEnvelope.de(NOTIFICATION_SENT, notificacion.orderId(), correlationId,
                NotificationSentPayload.de(notificacion, providerReference)));
    }

    /**
     * Publica que la notificacion no pudo entregarse (HU-304).
     *
     * <p>Es un hecho de negocio como el otro: el proveedor contesto que no. No va a DLQ y no
     * revierte nada; el pago sigue registrado y el pedido conserva su estado final (regla 12).
     *
     * @param correlationId correlacion del evento de pago que origino la notificacion
     */
    public void publicarFallida(Notification notificacion, java.util.UUID correlationId) {
        publicar(EventEnvelope.de(NOTIFICATION_FAILED, notificacion.orderId(), correlationId,
                NotificationFailedPayload.de(notificacion)));
    }

    private void publicar(EventEnvelope<? extends Record> evento) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar(evento);
                }
            });
        } else {
            enviar(evento);
        }
    }

    private void enviar(EventEnvelope<? extends Record> evento) {
        // La clave es el orderId, que es la clave de particion (regla 11, ADR-04): los hechos de
        // un pedido conservan su orden aunque el topico tenga varias particiones.
        String clave = evento.aggregateId().toString();
        try {
            kafka.send(notificationsTopic, clave, jackson.writeValueAsString(evento)).join();
            log.info("Evento publicado eventType={} eventId={} orderId={} correlationId={} topic={}",
                    evento.eventType(), evento.eventId(), evento.aggregateId(),
                    evento.correlationId(), notificationsTopic);
        } catch (Exception fallo) {
            // ADR-08: la notificacion queda ENVIADA y nadie lo sabra fuera de este registro.
            log.error("No se pudo publicar {} tras el commit. La notificacion queda registrada sin "
                            + "que nadie se entere. eventId={} orderId={} correlationId={} topic={} causa={}",
                    evento.eventType(), evento.eventId(), evento.aggregateId(),
                    evento.correlationId(), notificationsTopic, fallo.toString());
        }
    }
}
