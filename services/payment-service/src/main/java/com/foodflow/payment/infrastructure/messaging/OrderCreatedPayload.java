package com.foodflow.payment.infrastructure.messaging;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload de {@code OrderCreated} v1
 * ({@code contracts/events/v1/order-created.schema.json}).
 *
 * <p>El esquema declara {@code additionalProperties: false}, asi que un campo desconocido es
 * una incompatibilidad de contrato y no se ignora en silencio.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record OrderCreatedPayload(
        UUID orderId,
        String customerReference,
        BigDecimal total,
        String currency,
        String paymentToken,
        Contacto notificationContact) {

    /** Snapshot de contacto tal como viaja en el contrato (ADR-11). */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Contacto(String channel, String destination) {
    }
}
