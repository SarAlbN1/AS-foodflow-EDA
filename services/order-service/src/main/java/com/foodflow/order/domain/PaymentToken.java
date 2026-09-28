package com.foodflow.order.domain;

import java.util.Optional;

/**
 * Resultado de pago simulado que acompana al pedido (ADR-10, supuesto A-1).
 * El valor persistido lleva guion ({@code PAY-OK}, {@code PAY-FAIL}), por eso el nombre
 * de la constante y el valor del contrato se mantienen separados.
 */
public enum PaymentToken {

    PAY_OK("PAY-OK"),

    PAY_FAIL("PAY-FAIL");

    private final String valor;

    PaymentToken(String valor) {
        this.valor = valor;
    }

    /** Valor tal como viaja en la API y como se guarda en Order DB. */
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
