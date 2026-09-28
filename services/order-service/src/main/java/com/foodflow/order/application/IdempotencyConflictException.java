package com.foodflow.order.application;

/**
 * La misma {@code Idempotency-Key} se reutilizo con un cuerpo distinto (HU-107, criterio 3).
 *
 * <p>No es un reintento: es una solicitud nueva reusando una clave gastada, y atenderla crearia
 * un pedido distinto bajo una clave que ya identifica a otro. La traduce a {@code 409} con
 * {@code code: IDEMPOTENCY_CONFLICT} el manejador de errores de la capa {@code api}.
 */
public class IdempotencyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdempotencyConflictException(String key) {
        super("la clave " + key + " ya se uso con un cuerpo distinto");
    }
}
