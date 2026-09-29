package com.foodflow.order.infrastructure.messaging;

import java.math.BigDecimal;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.foodflow.order.application.OrderPaymentService;
import com.foodflow.order.application.PaymentResultCommand;
import com.foodflow.order.config.EventJsonConfig;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * Consume {@code payments.events} y aplica el resultado del pago al pedido (HU-104).
 *
 * <p>Es el unico punto de Order Service que lee de Kafka ({@code convenciones.md}). Tiene su
 * propio grupo, distinto del de Notification Service: los dos reciben cada resultado de pago de
 * forma independiente (D-6, reglas 6 y 12).
 *
 * <p><strong>Tipos.</strong> Procesa {@code PaymentApproved}. Cualquier otro tipo se ignora con
 * {@code DEBUG} y confirmacion de offset, igual que Payment Service con {@code orders.events};
 * {@code PaymentRejected} lo incorpora HU-105.
 *
 * <p><strong>Tres salidas distintas.</strong>
 * <ul>
 *   <li>Procesado o ya procesado: se confirma el offset despues del commit local (ADR-09).</li>
 *   <li>No procesable ({@link UnsupportedEventException}): se registra con {@code ERROR} y se
 *       confirma, porque repetirlo no lo arregla y no confirmarlo bloquearia la particion. HU-602
 *       lo llevara a la DLQ.</li>
 *   <li>Cualquier otro fallo (pedido inexistente, base caida): <strong>no</strong> se confirma. La
 *       excepcion sube al manejador de errores del contenedor, que reintenta.</li>
 * </ul>
 */
@Component
public class PaymentResultEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultEventConsumer.class);

    private static final String PAYMENT_APPROVED = "PaymentApproved";

    private static final Set<String> TIPOS_SOPORTADOS = Set.of(PAYMENT_APPROVED);

    /** Version del contrato que este consumidor entiende ({@code contracts/events/v1}). */
    private static final int VERSION_SOPORTADA = 1;

    /** Moneda unica del prototipo ({@code envelope.schema.json#/$defs/moneda}). */
    private static final String MONEDA_SOPORTADA = "COP";

    /**
     * Lectores estrictos: un campo fuera del contrato es una incompatibilidad y se rechaza. Se
     * activa aqui y no en el {@code ObjectMapper} compartido, que tambien usa el publicador de
     * {@code OrderCreated} (HU-103).
     */
    private final ObjectReader lectorEnvelope;
    private final ObjectReader lectorPagoAprobado;
    private final OrderPaymentService pagos;

    public PaymentResultEventConsumer(@Qualifier(EventJsonConfig.EVENT_OBJECT_MAPPER) ObjectMapper jackson,
            OrderPaymentService pagos) {
        this.lectorEnvelope = jackson.readerFor(ReceivedEventEnvelope.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.lectorPagoAprobado = jackson.readerFor(PaymentApprovedPayload.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.pagos = pagos;
    }

    @KafkaListener(topics = "${foodflow.kafka.payments-topic}",
            groupId = "${foodflow.kafka.payments-consumer-group}")
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
        ReceivedEventEnvelope envelope = leerEnvelope(registro);

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

        pagos.registrarPagoAprobado(aResultado(envelope));
    }

    private ReceivedEventEnvelope leerEnvelope(ConsumerRecord<String, String> registro) {
        String valor = registro.value();
        if (valor == null || valor.isBlank()) {
            throw new UnsupportedEventException("el registro no tiene cuerpo");
        }
        try {
            return lectorEnvelope.readValue(valor);
        } catch (Exception e) {
            throw new UnsupportedEventException("el cuerpo no es un envelope v1 legible: " + e.getMessage(), e);
        }
    }

    private static void exigirEnvelopeCompleto(ReceivedEventEnvelope envelope) {
        exigir(envelope.eventId() != null, "eventId es obligatorio");
        exigir(envelope.eventVersion() != null, "eventVersion es obligatorio");
        exigir(envelope.occurredAt() != null, "occurredAt es obligatorio");
        exigir(envelope.correlationId() != null, "correlationId es obligatorio");
        exigir(envelope.aggregateId() != null, "aggregateId es obligatorio");
        exigir(envelope.payload() != null && envelope.payload().isObject(), "payload es obligatorio");
    }

    private PaymentResultCommand aResultado(ReceivedEventEnvelope envelope) {
        PaymentApprovedPayload payload;
        try {
            payload = lectorPagoAprobado.readValue(envelope.payload());
        } catch (Exception e) {
            throw new UnsupportedEventException(
                    "el payload no cumple el contrato de PaymentApproved v1: " + e.getMessage(), e);
        }

        exigir(payload.paymentId() != null, "payload.paymentId es obligatorio");
        exigir(payload.orderId() != null, "payload.orderId es obligatorio");
        exigir(payload.amount() != null, "payload.amount es obligatorio");
        exigir(payload.currency() != null, "payload.currency es obligatorio");
        exigir(payload.transactionReference() != null, "PaymentApproved exige payload.transactionReference");
        exigir(payload.notificationContact() != null, "payload.notificationContact es obligatorio");
        // Regla arquitectonica 11 y ADR-04: aggregateId es SIEMPRE el orderId.
        exigir(envelope.aggregateId().equals(payload.orderId()),
                "aggregateId %s no coincide con payload.orderId %s"
                        .formatted(envelope.aggregateId(), payload.orderId()));
        exigir(payload.amount().compareTo(BigDecimal.ZERO) > 0, "payload.amount debe ser mayor que cero");
        exigir(MONEDA_SOPORTADA.equals(payload.currency()),
                "moneda %s no soportada: el prototipo solo opera en %s"
                        .formatted(payload.currency(), MONEDA_SOPORTADA));

        return new PaymentResultCommand(envelope.eventId(), envelope.correlationId(), payload.orderId(),
                payload.paymentId());
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new UnsupportedEventException(mensaje);
        }
    }
}
