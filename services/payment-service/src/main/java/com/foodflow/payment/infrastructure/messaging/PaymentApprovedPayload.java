package com.foodflow.payment.infrastructure.messaging;

import java.math.BigDecimal;
import java.util.UUID;

import com.foodflow.payment.domain.NotificationContact;
import com.foodflow.payment.domain.Payment;

/**
 * Payload de {@code PaymentApproved} v1
 * ({@code contracts/events/v1/payment-approved.schema.json}).
 *
 * <p>El esquema declara {@code additionalProperties: false}, asi que este record tiene
 * exactamente los campos del contrato y ninguno mas.
 *
 * @param notificationContact snapshot que Payment Service copio de {@code OrderCreated} y que
 *                            no guarda en Payment DB (ADR-11): viaja del evento de entrada al
 *                            de salida para que Notification Service no consulte otra base
 */
public record PaymentApprovedPayload(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        String transactionReference,
        Contacto notificationContact) {

    /** Snapshot de contacto tal como viaja en el contrato (ADR-11). */
    public record Contacto(String channel, String destination) {
    }

    /** Construye el payload a partir del pago ya persistido y del contacto recibido. */
    public static PaymentApprovedPayload de(Payment pago, String currency, NotificationContact contacto) {
        return new PaymentApprovedPayload(
                pago.id(),
                pago.orderId(),
                pago.amount(),
                currency,
                pago.transactionReference(),
                new Contacto(contacto.channel().name(), contacto.destination()));
    }
}
