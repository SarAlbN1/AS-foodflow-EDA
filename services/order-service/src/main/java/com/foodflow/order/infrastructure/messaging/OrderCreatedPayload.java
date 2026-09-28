package com.foodflow.order.infrastructure.messaging;

import java.math.BigDecimal;
import java.util.UUID;

import com.foodflow.order.domain.Order;

/**
 * Payload de {@code OrderCreated} v1
 * ({@code contracts/events/v1/order-created.schema.json}).
 *
 * <p>El esquema declara {@code additionalProperties: false}, asi que este record tiene
 * exactamente los campos del contrato y ninguno mas.
 *
 * @param notificationContact snapshot de contacto y canal capturado al crear el pedido
 *                            (ADR-11). Payment Service lo copia al evento de pago para que
 *                            Notification Service no tenga que consultar Order DB
 */
public record OrderCreatedPayload(
        UUID orderId,
        String customerReference,
        BigDecimal total,
        String currency,
        String paymentToken,
        Contacto notificationContact) {

    /** Moneda unica del prototipo ({@code envelope.schema.json#/$defs/moneda}). */
    public static final String MONEDA = "COP";

    /** Snapshot de contacto tal como viaja en el contrato (ADR-11). */
    public record Contacto(String channel, String destination) {
    }

    /** Construye el payload a partir del pedido ya persistido. */
    public static OrderCreatedPayload de(Order pedido) {
        return new OrderCreatedPayload(
                pedido.id(),
                pedido.customerReference(),
                pedido.total(),
                MONEDA,
                pedido.paymentToken().valor(),
                new Contacto(pedido.notificationChannel().name(), pedido.customerContact()));
    }
}
