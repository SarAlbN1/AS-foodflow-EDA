package com.foodflow.payment.domain;

/**
 * Resultado del pago de un pedido (HU-202, criterio 3).
 *
 * <p>El pago es determinista (ADR-10): {@code PAY-OK} produce {@link #APROBADO} y
 * {@code PAY-FAIL} produce {@link #RECHAZADO}. No hay estado intermedio ni pendiente: el
 * resultado se conoce al procesar {@code OrderCreated}, asi que un pago existe solo cuando
 * ya esta resuelto.
 *
 * <p>Los estados de negocio se nombran en espanol
 * ({@code docs/wiki/03-contratos/eventos.md}).
 */
public enum PaymentStatus {

    APROBADO,

    RECHAZADO
}
