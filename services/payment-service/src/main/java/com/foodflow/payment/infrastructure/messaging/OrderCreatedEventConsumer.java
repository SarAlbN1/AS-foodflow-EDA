package com.foodflow.payment.infrastructure.messaging;

import java.math.BigDecimal;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.foodflow.payment.application.PaymentApplicationService;
import com.foodflow.payment.application.StartPaymentCommand;
import com.foodflow.payment.config.EventJsonConfig;
import com.foodflow.payment.domain.NotificationChannel;
import com.foodflow.payment.domain.NotificationContact;
import com.foodflow.payment.domain.PaymentToken;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Consume {@code orders.events} y convierte cada {@code OrderCreated} en una orden de pago
 * validada (HU-201).
 *
 * <p>Es el unico punto del servicio que habla con Kafka
 * ({@code docs/wiki/04-implementacion/convenciones.md}). El pago lo dispara el evento, no una
 * llamada REST: Payment Service nunca consulta Order DB ni Order Service (reglas
 * arquitectonicas 2, 4 y 5).
 *
 * <p><strong>Ruido en el topico compartido.</strong> {@code orders.events} tambien transporta
 * {@code OrderStatusChanged}. Payment Service lo ignora con {@code DEBUG} y confirma el
 * offset, sin error y sin DLQ
 * ({@code docs/wiki/02-arquitectura/comportamiento-del-flujo.md}).
 *
 * <p><strong>Confirmacion del offset.</strong> Se confirma despues de que el caso de uso
 * retorna, es decir despues del commit de su transaccion local (ADR-09). El registro del
 * {@code eventId} en {@code processed_events}, dentro de esa misma transaccion, lo anade
 * HU-601.
 */
@Component
public class OrderCreatedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedEventConsumer.class);

    /** Unico tipo de {@code orders.events} que Payment Service procesa. */
    private static final String TIPO_SOPORTADO = "OrderCreated";

    /** Version del contrato que este consumidor entiende ({@code contracts/events/v1}). */
    private static final int VERSION_SOPORTADA = 1;

    /** Moneda unica del prototipo ({@code envelope.schema.json#/$defs/moneda}). */
    private static final String MONEDA_SOPORTADA = "COP";

    /** El envelope de entrada se enlaza con el payload sin interpretar, como arbol JSON. */
    private static final TypeReference<EventEnvelope<JsonNode>> TIPO_ENVELOPE = new TypeReference<>() {
    };

    private final ObjectMapper jackson;
    private final PaymentApplicationService pagos;

    public OrderCreatedEventConsumer(@Qualifier(EventJsonConfig.EVENT_OBJECT_MAPPER) ObjectMapper jackson,
            PaymentApplicationService pagos) {
        this.jackson = jackson;
        this.pagos = pagos;
    }

    /**
     * Procesa un registro de {@code orders.events}.
     *
     * <p>El offset se confirma siempre, tambien cuando el evento es no procesable: HU-201 no
     * tiene DLQ y dejar de confirmar bloquearia la particion, es decir todos los eventos
     * posteriores del mismo pedido. La publicacion en {@code orders.events.dlq} la anade
     * HU-602, y entonces la confirmacion pasa a hacerse tras escribir en la DLQ.
     */
    @KafkaListener(topics = "${foodflow.kafka.orders-topic}")
    public void consumir(ConsumerRecord<String, String> registro, Acknowledgment confirmacion) {
        // Un evento no procesable ya no se descarta en silencio: se deja subir para que el
        // manejador de HU-602 lo publique en <topico>.dlq sin reintentarlo. El offset se
        // confirma despues, cuando la DLQ ya lo tiene.
        procesar(registro);
        confirmacion.acknowledge();
    }

    private void procesar(ConsumerRecord<String, String> registro) {
        EventEnvelope<JsonNode> envelope = leerEnvelope(registro);

        if (!TIPO_SOPORTADO.equals(envelope.eventType())) {
            log.debug("Evento ignorado por tipo. eventType={} eventId={} aggregateId={}",
                    envelope.eventType(), envelope.eventId(), envelope.aggregateId());
            return;
        }

        exigirEnvelopeCompleto(envelope);

        if (envelope.eventVersion() != VERSION_SOPORTADA) {
            throw new UnsupportedEventException("eventVersion %d no soportada: este consumidor entiende la v%d"
                    .formatted(envelope.eventVersion(), VERSION_SOPORTADA));
        }

        try (var correlation = MDC.putCloseable("correlationId", envelope.correlationId().toString());
                var eventId = MDC.putCloseable("eventId", envelope.eventId().toString());
                var eventType = MDC.putCloseable("eventType", envelope.eventType());
                var orderId = MDC.putCloseable("orderId", envelope.aggregateId().toString())) {
            StartPaymentCommand orden = aOrdenDePago(envelope);
            pagos.iniciarPago(orden);
        }
    }

    private EventEnvelope<JsonNode> leerEnvelope(ConsumerRecord<String, String> registro) {
        String valor = registro.value();
        if (valor == null || valor.isBlank()) {
            throw new UnsupportedEventException("el registro no tiene cuerpo");
        }
        try {
            return jackson.readValue(valor, TIPO_ENVELOPE);
        } catch (Exception e) {
            throw new UnsupportedEventException("el cuerpo no es un envelope v1 legible: " + e.getMessage(), e);
        }
    }

    /**
     * Comprueba los campos del envelope que el contrato declara obligatorios. Solo se exigen
     * cuando el evento es de un tipo que este servicio procesa: un evento ajeno y mal formado
     * se ignora por tipo, no se convierte en un fallo de Payment Service.
     */
    private void exigirEnvelopeCompleto(EventEnvelope<JsonNode> envelope) {
        exigir(envelope.eventId() != null, "eventId es obligatorio");
        exigir(envelope.eventVersion() != null, "eventVersion es obligatorio");
        exigir(envelope.occurredAt() != null, "occurredAt es obligatorio");
        exigir(envelope.correlationId() != null, "correlationId es obligatorio");
        exigir(envelope.aggregateId() != null, "aggregateId es obligatorio");
        exigir(envelope.payload() != null && envelope.payload().isObject(), "payload es obligatorio");
    }

    private StartPaymentCommand aOrdenDePago(EventEnvelope<JsonNode> envelope) {
        OrderCreatedPayload payload;
        try {
            payload = jackson.treeToValue(envelope.payload(), OrderCreatedPayload.class);
        } catch (Exception e) {
            throw new UnsupportedEventException("el payload no cumple el contrato de OrderCreated v1: "
                    + e.getMessage(), e);
        }

        exigir(payload.orderId() != null, "payload.orderId es obligatorio");
        exigir(payload.total() != null, "payload.total es obligatorio");
        exigir(payload.currency() != null, "payload.currency es obligatorio");
        exigir(payload.paymentToken() != null, "payload.paymentToken es obligatorio");
        exigir(payload.notificationContact() != null, "payload.notificationContact es obligatorio");

        // Regla arquitectonica 11 y ADR-04: aggregateId es SIEMPRE el orderId, porque es la
        // clave de particion que conserva el orden de los eventos de un mismo pedido.
        exigir(envelope.aggregateId().equals(payload.orderId()),
                "aggregateId %s no coincide con payload.orderId %s"
                        .formatted(envelope.aggregateId(), payload.orderId()));

        exigir(payload.total().compareTo(BigDecimal.ZERO) > 0, "payload.total debe ser mayor que cero");

        // El contrato declara el importe como multiplo de 0.01 y Payment DB lo guarda como
        // NUMERIC(12,2). Un total con mas decimales no es un fallo transitorio: no se puede
        // representar, y reintentarlo no lo arregla. Se descarta aqui en vez de dejar que
        // reviente al ajustar la escala mas adelante.
        exigir(payload.total().stripTrailingZeros().scale() <= 2,
                "payload.total %s tiene mas de dos decimales".formatted(payload.total().toPlainString()));
        exigir(MONEDA_SOPORTADA.equals(payload.currency()),
                "moneda %s no soportada: el prototipo solo opera en %s"
                        .formatted(payload.currency(), MONEDA_SOPORTADA));

        PaymentToken token = PaymentToken.desdeValor(payload.paymentToken())
                .orElseThrow(() -> new UnsupportedEventException(
                        "paymentToken %s fuera del contrato (ADR-10)".formatted(payload.paymentToken())));

        return new StartPaymentCommand(
                envelope.eventId(),
                envelope.correlationId(),
                payload.orderId(),
                payload.total(),
                payload.currency(),
                token,
                aContacto(payload.notificationContact()));
    }

    private NotificationContact aContacto(OrderCreatedPayload.Contacto contacto) {
        NotificationChannel canal;
        try {
            canal = NotificationChannel.valueOf(contacto.channel());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnsupportedEventException(
                    "canal de notificacion %s fuera del contrato".formatted(contacto.channel()), e);
        }
        try {
            return new NotificationContact(canal, contacto.destination());
        } catch (IllegalArgumentException e) {
            throw new UnsupportedEventException("notificationContact incompleto: " + e.getMessage(), e);
        }
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new UnsupportedEventException(mensaje);
        }
    }
}
