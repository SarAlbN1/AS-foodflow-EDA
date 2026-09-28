package com.foodflow.order.api;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resuelve el {@code correlationId} de una peticion HTTP.
 *
 * <p>Normalmente entra por el gateway, que lo genera si falta y lo normaliza (HU-403). Order
 * Service vuelve a comprobarlo porque puede recibir trafico directo —en desarrollo o en las
 * pruebas— y porque el contrato de eventos lo tipa como UUID: un valor arbitrario del cliente
 * no debe acabar en el envelope de {@code OrderCreated}.
 */
public final class CorrelationId {

    /** Nombre de la cabecera, el mismo que usa el gateway. */
    public static final String CABECERA = "X-Correlation-Id";

    private static final Pattern UUID_VALIDO =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private CorrelationId() {
    }

    /** Devuelve el valor recibido si es un UUID; si falta o no lo es, genera uno nuevo. */
    public static UUID de(HttpServletRequest peticion) {
        return resolver(peticion == null ? null : peticion.getHeader(CABECERA));
    }

    /** Misma regla, sobre el valor crudo de la cabecera. */
    public static UUID resolver(String recibido) {
        if (recibido != null && UUID_VALIDO.matcher(recibido.strip()).matches()) {
            return UUID.fromString(recibido.strip().toLowerCase(java.util.Locale.ROOT));
        }
        return UUID.randomUUID();
    }
}
