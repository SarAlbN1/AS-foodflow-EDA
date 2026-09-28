package com.foodflow.order.validation;

import java.math.BigDecimal;

import com.foodflow.order.domain.NotificationChannel;
import com.foodflow.order.domain.PaymentToken;

/**
 * Datos de creacion de pedido ya validados y convertidos a tipos del dominio.
 * Solo {@link OrderValidator} lo construye, asi que su existencia prueba que la entrada paso
 * las comprobaciones del criterio 3 de HU-101.
 */
public record ValidatedOrderCommand(
        String customerReference,
        String customerContact,
        NotificationChannel notificationChannel,
        BigDecimal total,
        PaymentToken paymentToken) {
}
