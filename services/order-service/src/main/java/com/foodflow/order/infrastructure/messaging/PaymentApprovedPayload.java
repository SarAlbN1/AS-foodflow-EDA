package com.foodflow.order.infrastructure.messaging;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload de {@code PaymentApproved} v1 ({@code contracts/events/v1/payment-approved.schema.json}).
 *
 * <p>Lo produce Payment Service; Order Service solo lee lo que necesita para cambiar el estado,
 * pero el contrato prohibe propiedades extra, asi que un campo desconocido es un evento fuera de
 * contrato y se rechaza.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record PaymentApprovedPayload(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        String transactionReference,
        Contacto notificationContact) {

    /** Snapshot de contacto del contrato (ADR-11). Order Service no lo usa. */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Contacto(String channel, String destination) {
    }
}
