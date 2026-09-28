package com.foodflow.payment.domain;

import java.util.Optional;

/**
 * Token de pago determinista que viaja en {@code OrderCreated} (ADR-10, supuesto A-1):
 * {@code PAY-OK} produce un pago aprobado y {@code PAY-FAIL} uno rechazado.
 *
 * <p>El valor del contrato lleva guion, que no es valido en un identificador Java, por eso
 * el nombre de la constante y el valor se mantienen separados.
 *
 * <p>Es una copia propia del concepto: ningun servicio depende de clases de otro
 * (regla arquitectonica 8).
 */
public enum PaymentToken {

    PAY_OK("PAY-OK"),

    PAY_FAIL("PAY-FAIL");

    private final String valor;

    PaymentToken(String valor) {
        this.valor = valor;
    }

    /** Valor tal como viaja en el payload de {@code OrderCreated}. */
    public String valor() {
        return valor;
    }

    /** Devuelve el token correspondiente al valor del contrato, o vacio si no existe. */
    public static Optional<PaymentToken> desdeValor(String valor) {
        for (PaymentToken token : values()) {
            if (token.valor.equals(valor)) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }
}
