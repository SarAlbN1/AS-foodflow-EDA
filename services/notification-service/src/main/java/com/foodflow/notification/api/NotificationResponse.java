package com.foodflow.notification.api;

import java.time.Instant;
import java.util.UUID;

import com.foodflow.notification.application.ContactMasker;
import com.foodflow.notification.domain.Notification;

/**
 * Representacion de una notificacion en {@code GET /orders/{id}/notifications} (HU-305), con los
 * campos del esquema {@code Notification} de {@code contracts/api/openapi.yaml}.
 *
 * <p>{@code destination} sale <strong>siempre enmascarado</strong> ({@code a***@foodflow.test}),
 * con la misma regla que los registros: es dato personal y ninguna historia necesita el valor
 * completo. El contrato lo impone con un patron que rechaza un correo completo.
 */
public record NotificationResponse(
        UUID id,
        UUID orderId,
        UUID paymentId,
        String channel,
        String destination,
        String content,
        String status,
        int attempts,
        String failureCode,
        Instant createdAt,
        Instant updatedAt) {

    static NotificationResponse from(Notification notificacion) {
        return new NotificationResponse(
                notificacion.id(),
                notificacion.orderId(),
                notificacion.paymentId(),
                notificacion.channel().name(),
                ContactMasker.mask(notificacion.destination()),
                notificacion.content(),
                notificacion.status().name(),
                notificacion.attempts(),
                notificacion.failureCode(),
                notificacion.createdAt(),
                notificacion.updatedAt());
    }
}
