package com.foodflow.payment.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.foodflow.payment.application.StartPaymentCommand;
import com.foodflow.payment.config.EventJsonConfig;
import com.foodflow.payment.domain.Payment;

import tools.jackson.databind.ObjectMapper;

/**
 * Publica el resultado del pago en {@code payments.events} (HU-203).
 *
 * <p>Es el unico punto del servicio que produce eventos. Payment Service no llama por REST a
 * Order Service ni a Notification Service: publica el hecho y cada uno reacciona por su cuenta,
 * en su propio grupo de consumidores (reglas arquitectonicas 4, 6 y 10).
 *
 * <p><strong>Despues del commit (ADR-08).</strong> La publicacion se registra para ejecutarse
 * cuando la transaccion que persiste el pago ya ha hecho commit, asi que un pago que no llega a
 * guardarse nunca produce evento. La contrapartida es la ventana de escritura dual que ADR-08
 * acepta: si el commit sale bien y la publicacion falla, el pago queda registrado sin que nadie
 * se entere. Se registra el fallo y no se hace nada mas: no hay Outbox ni reconciliacion.
 *
 * <p><strong>Alcance de HU-203.</strong> Solo se publica {@code PaymentApproved}. El evento de
 * rechazo lo anade HU-204; hasta entonces un pago {@code RECHAZADO} se persiste y no produce
 * evento, y el publicador lo deja dicho en un {@code WARN} para que no parezca un fallo.
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    /** Nombre del evento en el catalogo de {@code docs/wiki/03-contratos/eventos.md}. */
    static final String PAYMENT_APPROVED = "PaymentApproved";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper jackson;
    private final String paymentsTopic;

    public PaymentEventPublisher(
            KafkaTemplate<String, String> kafka,
            @Qualifier(EventJsonConfig.EVENT_OBJECT_MAPPER) ObjectMapper jackson,
            @Value("${foodflow.kafka.payments-topic}") String paymentsTopic) {
        this.kafka = kafka;
        this.jackson = jackson;
        this.paymentsTopic = paymentsTopic;
    }

    /**
     * Publica el resultado de un pago ya persistido.
     *
     * <p>Si hay una transaccion activa, la publicacion espera a su commit; si no la hay, se
     * envia de inmediato. Llamar a este metodo nunca hace fallar el procesamiento del pago: un
     * error de publicacion se registra y se traga, porque el pago ya esta guardado.
     *
     * @param orden la orden que origino el pago; de ella salen el {@code correlationId}, la
     *              moneda y el snapshot de contacto, que no estan en Payment DB
     */
    public void publicarResultado(Payment pago, StartPaymentCommand orden) {
        if (!pago.aprobado()) {
            // HU-204 publica PaymentRejected. Hasta entonces no hay evento de rechazo.
            log.warn("Pago rechazado sin evento: PaymentRejected lo publica HU-204. "
                    + "orderId={} paymentId={} correlationId={}",
                    pago.orderId(), pago.id(), orden.correlationId());
            return;
        }

        EventEnvelope<PaymentApprovedPayload> evento = EventEnvelope.de(
                PAYMENT_APPROVED, pago.orderId(), orden.correlationId(),
                PaymentApprovedPayload.de(pago, orden.currency(), orden.contact()));

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
        // La clave es el orderId: es la clave de particion (regla 11, ADR-04), lo que garantiza
        // que Order y Notification reciben los eventos de un pedido en orden.
        String clave = evento.aggregateId().toString();
        try {
            kafka.send(paymentsTopic, clave, jackson.writeValueAsString(evento)).join();
            log.info("Evento publicado eventType={} eventId={} orderId={} correlationId={} topic={}",
                    evento.eventType(), evento.eventId(), evento.aggregateId(),
                    evento.correlationId(), paymentsTopic);
        } catch (Exception e) {
            // ADR-08: el pago queda registrado y nadie lo reconcilia.
            log.error("No se pudo publicar {} tras el commit. El pago queda registrado sin que nadie "
                            + "se entere. eventId={} orderId={} correlationId={} topic={} causa={}",
                    evento.eventType(), evento.eventId(), evento.aggregateId(),
                    evento.correlationId(), paymentsTopic, e.toString());
        }
    }
}
