package com.foodflow.notification.infrastructure.messaging;

import java.math.BigDecimal;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.foodflow.notification.application.NotificationDispatcher;
import com.foodflow.notification.application.NotifyPaymentResultCommand;
import com.foodflow.notification.config.EventJsonConfig;
import com.foodflow.notification.domain.NotificationChannel;

import tools.jackson.databind.ObjectMapper;

/**
 * Consume {@code payments.events} y convierte cada resultado de pago en una orden de
 * notificacion validada (HU-301).
 *
 * <p>Es el unico punto del servicio que habla con Kafka
 * ({@code docs/wiki/04-implementacion/convenciones.md}).
 *
 * <p><strong>Reacciona al pago, no al pedido.</strong> Order Service consume el mismo topico en
 * otro grupo: los dos reciben el resultado de forma independiente y esta notificacion no espera
 * a que el pedido cambie de estado (decision D-6, reglas 10 y 13).
 *
 * <p><strong>Ruido en el topico.</strong> {@code payments.events} solo transporta los dos
 * resultados de pago hoy, pero cualquier otro tipo se ignora con {@code DEBUG} y confirmacion de
 * offset, igual que hace Payment Service con {@code orders.events}.
 *
 * <p><strong>Confirmacion del offset.</strong> Se confirma despues de que el caso de uso retorna,
 * es decir despues del commit de su transaccion local, que incluye el registro del
 * {@code eventId} en {@code processed_events} (ADR-09).
 */
@Component
public class PaymentResultEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultEventConsumer.class);

    private static final String PAYMENT_APPROVED = "PaymentApproved";
    private static final String PAYMENT_REJECTED = "PaymentRejected";

    /** Los dos tipos que este servicio procesa. */
    private static final Set<String> TIPOS_SOPORTADOS = Set.of(PAYMENT_APPROVED, PAYMENT_REJECTED);

    /** Version del contrato que este consumidor entiende ({@code contracts/events/v1}). */
    private static final int VERSION_SOPORTADA = 1;

    /** Moneda unica del prototipo ({@code envelope.schema.json#/$defs/moneda}). */
    private static final String MONEDA_SOPORTADA = "COP";

    private final ObjectMapper jackson;
    private final NotificationDispatcher notificaciones;

    public PaymentResultEventConsumer(
            @Qualifier(EventJsonConfig.EVENT_OBJECT_MAPPER) ObjectMapper jackson,
            NotificationDispatcher notificaciones) {
        this.jackson = jackson;
        this.notificaciones = notificaciones;
    }

    /**
     * Procesa un registro de {@code payments.events}.
     *
     * <p>El offset se confirma siempre, tambien cuando el evento es no procesable: HU-301 no
     * tiene DLQ y dejar de confirmarlo bloquearia la particion, es decir todos los eventos
     * posteriores del mismo pedido. La publicacion en {@code payments.events.dlq} la anade
     * HU-602.
     */
    @KafkaListener(topics = "${foodflow.kafka.payments-topic}")
    public void consumir(ConsumerRecord<String, String> registro, Acknowledgment confirmacion) {
        try {
            procesar(registro);
        } catch (UnsupportedEventException e) {
            log.error("Evento no procesable, descartado. topic={} partition={} offset={} key={} motivo={}",
                    registro.topic(), registro.partition(), registro.offset(), registro.key(), e.getMessage());
        }
        confirmacion.acknowledge();
    }

    private void procesar(ConsumerRecord<String, String> registro) {
        EventEnvelope envelope = leerEnvelope(registro);

        if (!TIPOS_SOPORTADOS.contains(envelope.eventType())) {
            log.debug("Evento ignorado por tipo. eventType={} eventId={} aggregateId={}",
                    envelope.eventType(), envelope.eventId(), envelope.aggregateId());
            return;
        }

        exigirEnvelopeCompleto(envelope);

        if (envelope.eventVersion() != VERSION_SOPORTADA) {
            throw new UnsupportedEventException("eventVersion %d no soportada: este consumidor entiende la v%d"
                    .formatted(envelope.eventVersion(), VERSION_SOPORTADA));
        }

        notificaciones.procesar(aOrdenDeNotificacion(envelope));
    }

    private EventEnvelope leerEnvelope(ConsumerRecord<String, String> registro) {
        String valor = registro.value();
        if (valor == null || valor.isBlank()) {
            throw new UnsupportedEventException("el registro no tiene cuerpo");
        }
        try {
            return jackson.readValue(valor, EventEnvelope.class);
        } catch (Exception e) {
            throw new UnsupportedEventException("el cuerpo no es un envelope v1 legible: " + e.getMessage(), e);
        }
    }

    /**
     * Comprueba los campos del envelope que el contrato declara obligatorios. Solo se exigen
     * cuando el evento es de un tipo que este servicio procesa: un evento ajeno y mal formado se
     * ignora por tipo, no se convierte en un fallo de Notification Service.
     */
    private void exigirEnvelopeCompleto(EventEnvelope envelope) {
        exigir(envelope.eventId() != null, "eventId es obligatorio");
        exigir(envelope.eventVersion() != null, "eventVersion es obligatorio");
        exigir(envelope.occurredAt() != null, "occurredAt es obligatorio");
        exigir(envelope.correlationId() != null, "correlationId es obligatorio");
        exigir(envelope.aggregateId() != null, "aggregateId es obligatorio");
        exigir(envelope.payload() != null && envelope.payload().isObject(), "payload es obligatorio");
    }

    private NotifyPaymentResultCommand aOrdenDeNotificacion(EventEnvelope envelope) {
        PaymentResultPayload payload;
        try {
            payload = jackson.treeToValue(envelope.payload(), PaymentResultPayload.class);
        } catch (Exception e) {
            throw new UnsupportedEventException("el payload no cumple el contrato de %s v1: %s"
                    .formatted(envelope.eventType(), e.getMessage()), e);
        }

        boolean aprobado = PAYMENT_APPROVED.equals(envelope.eventType());

        exigir(payload.paymentId() != null, "payload.paymentId es obligatorio");
        exigir(payload.orderId() != null, "payload.orderId es obligatorio");
        exigir(payload.amount() != null, "payload.amount es obligatorio");
        exigir(payload.currency() != null, "payload.currency es obligatorio");
        exigir(payload.notificationContact() != null, "payload.notificationContact es obligatorio");
        // Cada tipo exige su propio campo distintivo.
        exigir(!aprobado || payload.transactionReference() != null,
                "PaymentApproved exige payload.transactionReference");
        exigir(aprobado || payload.reasonCode() != null,
                "PaymentRejected exige payload.reasonCode");

        // Regla arquitectonica 11 y ADR-04: aggregateId es SIEMPRE el orderId.
        exigir(envelope.aggregateId().equals(payload.orderId()),
                "aggregateId %s no coincide con payload.orderId %s"
                        .formatted(envelope.aggregateId(), payload.orderId()));

        exigir(payload.amount().compareTo(BigDecimal.ZERO) > 0, "payload.amount debe ser mayor que cero");
        exigir(MONEDA_SOPORTADA.equals(payload.currency()),
                "moneda %s no soportada: el prototipo solo opera en %s"
                        .formatted(payload.currency(), MONEDA_SOPORTADA));

        NotificationChannel canal = canal(payload.notificationContact());
        String destino = payload.notificationContact().destination();
        exigir(destino != null && !destino.isBlank(), "payload.notificationContact.destination es obligatorio");

        return new NotifyPaymentResultCommand(
                envelope.eventId(),
                envelope.correlationId(),
                payload.orderId(),
                payload.paymentId(),
                payload.amount(),
                payload.currency(),
                aprobado,
                canal,
                destino);
    }

    private NotificationChannel canal(PaymentResultPayload.Contacto contacto) {
        try {
            return NotificationChannel.valueOf(contacto.channel());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnsupportedEventException(
                    "canal de notificacion %s fuera del contrato".formatted(contacto.channel()), e);
        }
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new UnsupportedEventException(mensaje);
        }
    }
}
