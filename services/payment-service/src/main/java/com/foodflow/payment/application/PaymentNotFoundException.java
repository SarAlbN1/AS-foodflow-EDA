package com.foodflow.payment.application;

import java.util.UUID;

/**
 * No hay pago registrado para el pedido (HU-205). La API lo traduce a {@code 404} con
 * {@code code: NOT_FOUND}: el cliente lo interpreta como «aun no procesado» mientras el pedido
 * siga en {@code CREADO}.
 */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID orderId) {
        super("todavia no hay un pago registrado para el pedido " + orderId);
    }
}
