package com.foodflow.order.application;

import java.util.UUID;

/**
 * No existe un pedido con el identificador consultado (criterio 3 de HU-102). La traduce a
 * {@code 404} en formato Problem Details el manejador de errores de la capa {@code api}.
 */
public class OrderNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final UUID orderId;

    public OrderNotFoundException(UUID orderId) {
        super("no existe un pedido con id " + orderId);
        this.orderId = orderId;
    }

    public UUID orderId() {
        return orderId;
    }
}
