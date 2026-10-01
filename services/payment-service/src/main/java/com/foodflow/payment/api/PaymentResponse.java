package com.foodflow.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.foodflow.payment.domain.Payment;
import com.foodflow.payment.domain.PaymentStatus;

/**
 * Representacion HTTP del pago (esquema {@code Payment} de {@code contracts/api/openapi.yaml}).
 *
 * <p>{@code transactionReference} existe siempre, tambien en un pago rechazado, para poder
 * rastrear el intento; {@code reasonCode} solo en un pago rechazado y {@code null} en uno aprobado
 * (criterio 2: «referencia cuando corresponda»).
 */
public record PaymentResponse(
        UUID id,
        UUID orderId,
        PaymentStatus status,
        BigDecimal amount,
        String transactionReference,
        String reasonCode,
        Instant createdAt,
        Instant updatedAt) {

    static PaymentResponse from(Payment pago) {
        return new PaymentResponse(pago.id(), pago.orderId(), pago.status(), pago.amount(),
                pago.transactionReference(), pago.reasonCode(), pago.createdAt(), pago.updatedAt());
    }
}
