package com.foodflow.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.foodflow.order.domain.Order;

/**
 * Representacion HTTP del pedido. Incluye el snapshot de notificacion de ADR-11 y las fechas,
 * que el frontend usa para mostrar un estado que cambia de forma eventual.
 */
public record OrderResponse(
        UUID id,
        String customerReference,
        String customerContact,
        String notificationChannel,
        BigDecimal total,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    static OrderResponse from(Order pedido) {
        return new OrderResponse(
                pedido.id(),
                pedido.customerReference(),
                pedido.customerContact(),
                pedido.notificationChannel().name(),
                pedido.total(),
                pedido.status().name(),
                pedido.createdAt(),
                pedido.updatedAt());
    }
}
