package com.foodflow.gateway.config;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Respuesta del gateway cuando el servicio destino no responde (conexion rechazada o sin
 * respuesta): {@code 503} en Problem Details con {@code code: DEPENDENCY_UNAVAILABLE}, del catalogo
 * de {@code contracts/api/openapi.yaml}. Es la unica respuesta que el gateway produce por si mismo
 * en las rutas de pedidos; nunca expone la ubicacion interna del servicio.
 */
final class DependencyUnavailable {

    private static final Logger log = LoggerFactory.getLogger(DependencyUnavailable.class);

    private DependencyUnavailable() {
    }

    static ServerResponse response(Throwable error, ServerRequest request) {
        // CorrelationIdFilter ya resolvio el valor antes de llegar aqui; el respaldo solo protege
        // ante un cambio futuro en el orden de los filtros.
        String correlationId = Objects.requireNonNullElseGet(
                request.headers().firstHeader(CorrelationIdFilter.CABECERA), () -> UUID.randomUUID().toString());
        log.warn("Servicio destino no disponible correlationId={} metodo={} ruta={} causa={}",
                correlationId, request.method(), request.path(), error.getMessage());

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "el servicio que atiende la solicitud no esta disponible; intente de nuevo");
        problema.setType(URI.create("https://foodflow.local/problems/dependency-unavailable"));
        problema.setTitle("Servicio no disponible");
        problema.setProperty("code", "DEPENDENCY_UNAVAILABLE");
        problema.setProperty("correlationId", correlationId);

        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
    }
}
