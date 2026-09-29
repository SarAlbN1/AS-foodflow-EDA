package com.foodflow.order.infrastructure.messaging;

import java.util.UUID;

import com.foodflow.order.domain.Order;
import com.foodflow.order.domain.OrderStatus;

/**
 * Payload de {@code OrderStatusChanged} v1 ({@code contracts/events/v1/order-status-changed.schema.json}).
 *
 * <p>Se publica cuando el pedido cambia de estado tras el resultado del pago (HU-106). Ningun
 * servicio depende de el en el prototipo: Notification reacciona a {@code payments.events} (D-6) y
 * Payment lo ignora. El contacto viaja solo para consumidores futuros (ADR-11).
 */
public record OrderStatusChangedPayload(
        UUID orderId,
        String previousStatus,
        String newStatus,
        OrderCreatedPayload.Contacto notificationContact) {

    /** Construye el payload a partir del pedido ya en su estado nuevo y del estado del que partio. */
    public static OrderStatusChangedPayload de(Order pedido, OrderStatus anterior) {
        return new OrderStatusChangedPayload(
                pedido.id(),
                anterior.name(),
                pedido.status().name(),
                new OrderCreatedPayload.Contacto(pedido.notificationChannel().name(), pedido.customerContact()));
    }
}
