package com.foodflow.payment.infrastructure.messaging;

import java.math.BigDecimal;
import java.util.UUID;

import com.foodflow.payment.domain.NotificationContact;
import com.foodflow.payment.domain.Payment;

/**
 * Payload de {@code PaymentRejected} v1
 * ({@code contracts/events/v1/payment-rejected.schema.json}).
 *
 * <p>El esquema declara {@code additionalProperties: false}, asi que este record tiene
 * exactamente los campos del contrato y ninguno mas.
 *
 * <p>Se diferencia de {@code PaymentApproved} en un solo campo: donde el aprobado lleva
 * {@code transactionReference}, este lleva {@code reasonCode}. Son dos hechos distintos, no un
 * evento con un indicador de resultado: un consumidor puede suscribirse solo al que le importa
 * y no tiene que interpretar ningun campo para saber que paso.
 *
 * @param reasonCode motivo del rechazo, del catalogo de {@code RejectionReason}
 */
public record PaymentRejectedPayload(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        String reasonCode,
        Contacto notificationContact) {

    /** Snapshot de contacto tal como viaja en el contrato (ADR-11). */
    public record Contacto(String channel, String destination) {
    }

    /** Construye el payload a partir del pago ya persistido y del contacto recibido. */
    public static PaymentRejectedPayload de(Payment pago, String currency, NotificationContact contacto) {
        return new PaymentRejectedPayload(
                pago.id(),
                pago.orderId(),
                pago.amount(),
                currency,
                pago.reasonCode(),
                new Contacto(contacto.channel().name(), contacto.destination()));
    }
}
