package com.foodflow.notification.infrastructure.messaging;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload de {@code PaymentApproved} y {@code PaymentRejected} v1
 * ({@code contracts/events/v1/payment-approved.schema.json} y
 * {@code payment-rejected.schema.json}).
 *
 * <p>Los dos esquemas comparten todos los campos que Notification Service necesita y se
 * diferencian en uno que no usa: el aprobado lleva {@code transactionReference} y el rechazado
 * {@code reasonCode}. Se enlazan con un solo record y ese campo se acepta como opcional, en vez
 * de duplicar la clase para un dato que este servicio no lee.
 *
 * <p>{@code notificationContact} es lo que hace que este servicio no tenga que consultar Order
 * DB ni Payment DB (ADR-11, criterio 5).
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record PaymentResultPayload(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        String transactionReference,
        String reasonCode,
        Contacto notificationContact) {

    /** Snapshot de contacto tal como viaja en el contrato (ADR-11). */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Contacto(String channel, String destination) {
    }
}
