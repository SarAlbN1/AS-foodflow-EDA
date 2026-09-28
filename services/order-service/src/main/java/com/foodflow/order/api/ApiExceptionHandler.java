package com.foodflow.order.api;

import java.net.URI;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.foodflow.order.application.OrderNotFoundException;
import com.foodflow.order.validation.OrderValidationException;

/**
 * Traduce los errores a Problem Details (RFC 9457) con el formato de la pagina
 * {@code docs/wiki/03-contratos/api-rest.md}. Nunca devuelve trazas de pila.
 *
 * <p>Cubre el {@code 400} de entrada invalida (HU-101 y HU-102), el {@code 404} de un pedido
 * inexistente (HU-102) y el {@code 500} de un fallo no controlado. El catalogo de {@code code}
 * lo fija {@code contracts/api/openapi.yaml} (HU-404).
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String BASE_PROBLEMAS = "https://foodflow.local/problems/";
    private static final String CABECERA_CORRELACION = "X-Correlation-Id";

    /** Entrada invalida: no se crea ningun registro (criterio 4 de HU-101). */
    @ExceptionHandler(OrderValidationException.class)
    ProblemDetail entradaInvalida(OrderValidationException excepcion, HttpServletRequest peticion) {
        return problema(HttpStatus.BAD_REQUEST, "validation-error", "Solicitud invalida",
                excepcion.getMessage(), "VALIDATION_ERROR", peticion);
    }

    /** Cuerpo ausente, JSON mal formado o un tipo que no corresponde (por ejemplo un total textual). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail cuerpoIlegible(HttpMessageNotReadableException excepcion, HttpServletRequest peticion) {
        return problema(HttpStatus.BAD_REQUEST, "validation-error", "Solicitud invalida",
                "el cuerpo de la solicitud esta ausente, mal formado o tiene tipos invalidos",
                "VALIDATION_ERROR", peticion);
    }

    /** Identificador de ruta que no es un UUID, por ejemplo {@code GET /orders/abc} (HU-102). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametroInvalido(MethodArgumentTypeMismatchException excepcion, HttpServletRequest peticion) {
        return problema(HttpStatus.BAD_REQUEST, "validation-error", "Solicitud invalida",
                excepcion.getName() + ": debe ser un UUID valido", "VALIDATION_ERROR", peticion);
    }

    /** El pedido consultado no existe (criterio 3 de HU-102). */
    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail noEncontrado(OrderNotFoundException excepcion, HttpServletRequest peticion) {
        return problema(HttpStatus.NOT_FOUND, "not-found", "Recurso no encontrado",
                excepcion.getMessage(), "NOT_FOUND", peticion);
    }

    /** Cualquier fallo no previsto: se registra completo y se responde sin detalle interno. */
    @ExceptionHandler(Exception.class)
    ProblemDetail errorNoControlado(Exception excepcion, HttpServletRequest peticion) {
        String correlationId = correlationId(peticion);
        log.error("Error no controlado correlationId={} ruta={}", correlationId, peticion.getRequestURI(), excepcion);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Error interno",
                "la solicitud no pudo procesarse", "INTERNAL_ERROR", peticion);
    }

    private static ProblemDetail problema(HttpStatus estado, String tipo, String titulo, String detalle,
            String codigo, HttpServletRequest peticion) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create(BASE_PROBLEMAS + tipo));
        problema.setTitle(titulo);
        problema.setProperty("code", codigo);
        problema.setProperty("correlationId", correlationId(peticion));
        return problema;
    }

    /** Misma regla que usa el resto del servicio: ver {@link CorrelationId}. */
    private static String correlationId(HttpServletRequest peticion) {
        return CorrelationId.de(peticion).toString();
    }
}
