package com.foodflow.order.validation;

import java.util.List;

/**
 * Entrada invalida para crear un pedido. La traduce a {@code 400} en formato Problem Details
 * el manejador de errores de la capa {@code api}; nunca llega a persistirse nada.
 */
public class OrderValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<Violation> violations;

    public OrderValidationException(List<Violation> violations) {
        super(violations.stream().map(Violation::describir).reduce((a, b) -> a + "; " + b).orElse("entrada invalida"));
        this.violations = List.copyOf(violations);
    }

    public List<Violation> violations() {
        return violations;
    }

    /** Campo rechazado y motivo, en el formato {@code campo: motivo} que usa el {@code detail}. */
    public record Violation(String campo, String motivo) {

        public String describir() {
            return campo + ": " + motivo;
        }
    }
}
