package com.foodflow.notification.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.foodflow.notification.domain.NotificationChannel;

/**
 * Orden de notificar el resultado de un pago, ya validada contra el contrato del evento.
 *
 * <p>Todo lo que contiene llega en el evento de pago: el destino y el canal salen del snapshot
 * de ADR-11, asi que Notification Service no consulta Order DB ni Payment DB (HU-301,
 * criterio 5).
 *
 * @param eventId       identificador del evento consumido; sustenta la idempotencia (ADR-09)
 * @param correlationId correlacion de extremo a extremo del flujo del pedido
 * @param aprobado      resultado del pago: decide el texto del mensaje
 */
public record NotifyPaymentResultCommand(
        UUID eventId,
        UUID correlationId,
        UUID orderId,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        boolean aprobado,
        NotificationChannel channel,
        String destination) {
}
