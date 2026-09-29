package com.foodflow.notification.infrastructure.messaging;

import java.util.UUID;

import com.foodflow.notification.domain.Notification;

/**
 * Payload de {@code NotificationFailed} v1
 * ({@code contracts/events/v1/notification-failed.schema.json}).
 *
 * <p>Los seis campos del esquema, que declara {@code additionalProperties: false}. Igual que
 * {@code NotificationSent}, <strong>no lleva el destino ni el contenido</strong>.
 *
 * <p>{@code failureCode} y {@code attempts} son lo que exige el criterio 4: una causa tecnica
 * suficiente para diagnosticar sin abrir la base. El catalogo de motivos esta en
 * {@code docs/wiki/03-contratos/proveedor-notificaciones.md}.
 */
record NotificationFailedPayload(
        UUID notificationId,
        UUID orderId,
        UUID paymentId,
        String channel,
        String failureCode,
        int attempts) {

    static NotificationFailedPayload de(Notification notificacion) {
        return new NotificationFailedPayload(
                notificacion.id(),
                notificacion.orderId(),
                notificacion.paymentId(),
                notificacion.channel().name(),
                notificacion.failureCode(),
                notificacion.attempts());
    }
}
