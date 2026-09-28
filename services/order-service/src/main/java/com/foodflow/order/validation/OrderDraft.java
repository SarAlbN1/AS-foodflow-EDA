package com.foodflow.order.validation;

import java.math.BigDecimal;

/**
 * Datos de creacion de pedido tal como llegan del borde, sin validar ni convertir.
 * Existe para que {@link OrderValidator} no dependa de los DTO HTTP de la capa {@code api}.
 */
public record OrderDraft(
        String customerReference,
        String customerContact,
        String notificationChannel,
        BigDecimal total,
        String paymentToken) {
}
