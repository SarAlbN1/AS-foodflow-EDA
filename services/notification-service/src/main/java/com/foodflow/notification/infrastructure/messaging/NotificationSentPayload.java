package com.foodflow.notification.infrastructure.messaging;

import java.util.UUID;

import com.foodflow.notification.domain.Notification;

/**
 * Payload de {@code NotificationSent} v1
 * ({@code contracts/events/v1/notification-sent.schema.json}).
 *
 * <p>El esquema declara {@code additionalProperties: false}, asi que estos cinco campos son
 * exactamente los que viajan. <strong>No lleva el destino ni el contenido</strong>: el evento
 * dice que la notificacion se envio, no lo que se envio, y el destino es dato personal que
 * ningun consumidor de este topico necesita.
 *
 * @param providerReference referencia que devolvio el proveedor; nunca vacia, porque el contrato
 *                          exige al menos un caracter
 */
record NotificationSentPayload(
        UUID notificationId,
        UUID orderId,
        UUID paymentId,
        String channel,
        String providerReference) {

    static NotificationSentPayload de(Notification notificacion, String providerReference) {
        return new NotificationSentPayload(
                notificacion.id(),
                notificacion.orderId(),
                notificacion.paymentId(),
                notificacion.channel().name(),
                providerReference);
    }
}
