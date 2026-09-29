package com.foodflow.order.infrastructure.messaging;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload de {@code PaymentRejected} v1 ({@code contracts/events/v1/payment-rejected.schema.json}).
 *
 * <p>Lo produce Payment Service; Order Service solo necesita el pedido y el pago, pero el
 * contrato prohibe propiedades extra, asi que un campo desconocido se rechaza.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record PaymentRejectedPayload(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        String reasonCode,
        PaymentApprovedPayload.Contacto notificationContact) {
}
