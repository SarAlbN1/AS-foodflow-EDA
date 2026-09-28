package com.foodflow.payment.domain;

/**
 * Catalogo de motivos de rechazo que viajan en {@code PaymentRejected.payload.reasonCode}
 * ({@code contracts/events/v1/payment-rejected.schema.json}, que deja el catalogo a HU-202).
 *
 * <p>El prototipo tiene un unico motivo porque el pago es determinista (ADR-10): un pago se
 * rechaza cuando, y solo cuando, el pedido llega con {@code PAY-FAIL}. No se inventan motivos
 * que el prototipo no pueda producir; el valor coincide con el del ejemplo versionado
 * {@code contracts/events/v1/examples/validos/payment-rejected.json}.
 */
public final class RejectionReason {

    /** Unico motivo del prototipo: el pedido llego con el token de fallo. */
    public static final String PAGO_RECHAZADO_POR_TOKEN = "PAGO_RECHAZADO_POR_TOKEN";

    private RejectionReason() {
    }
}
