package com.foodflow.order.api;

/**
 * La cabecera {@code Idempotency-Key} falta o no es utilizable (HU-107, criterio 1).
 *
 * <p>El contrato la declara obligatoria ({@code contracts/api/openapi.yaml}) porque es lo que
 * protege la entrada ante un doble clic o un reintento del cliente: sin ella no hay forma de
 * saber si una segunda solicitud identica es un pedido nuevo o el mismo reenviado.
 *
 * <p>La traduce a {@code 400} con {@code code: VALIDATION_ERROR} el manejador de errores, con el
 * mismo formato que el resto de fallos de entrada.
 */
public class MissingIdempotencyKeyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MissingIdempotencyKeyException(String motivo) {
        super(OrderController.IDEMPOTENCY_KEY + ": " + motivo);
    }
}
