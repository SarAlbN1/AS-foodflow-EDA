package com.foodflow.notification.api;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Traduce los errores de la API de consulta a Problem Details (RFC 9457), con el mismo formato y
 * el mismo catalogo de {@code code} que el resto del prototipo ({@code contracts/api/openapi.yaml},
 * {@code docs/wiki/03-contratos/api-rest.md}). Nunca devuelve trazas de pila.
 *
 * <p>El formato se repite aqui en lugar de compartirse con Order Service: los servicios no
 * comparten codigo (regla 8).
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String BASE_PROBLEMAS = "https://foodflow.local/problems/";
    private static final String CABECERA_CORRELACION = "X-Correlation-Id";
    private static final Pattern UUID_VALIDO =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** Identificador de ruta que no es un UUID, por ejemplo {@code GET /orders/abc/notifications}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametroInvalido(MethodArgumentTypeMismatchException excepcion, HttpServletRequest peticion) {
        return problema(HttpStatus.BAD_REQUEST, "validation-error", "Solicitud invalida",
                excepcion.getName() + ": debe ser un UUID valido", "VALIDATION_ERROR", peticion);
    }

    /**
     * Cualquier fallo no previsto: se registra completo y se responde sin detalle interno.
     *
     * <p>Los errores que Spring ya clasifica con su estado (ruta inexistente {@code 404}, metodo no
     * admitido {@code 405}...) implementan {@link ErrorResponse} y conservan ese estado: sin esta
     * distincion, {@code /actuator/env} responderia {@code 500} en lugar de {@code 404}.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail errorNoControlado(Exception excepcion, HttpServletRequest peticion) {
        if (excepcion instanceof ErrorResponse deSpring) {
            ProblemDetail problema = deSpring.getBody();
            problema.setProperty("correlationId", correlationId(peticion));
            return problema;
        }
        log.error("Error no controlado correlationId={} ruta={}", correlationId(peticion),
                peticion.getRequestURI(), excepcion);
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

    /**
     * El {@code correlationId} que puso el gateway (HU-403). Si falta o no es un UUID —trafico
     * directo en desarrollo o en pruebas— se genera uno, igual que en Order Service.
     */
    private static String correlationId(HttpServletRequest peticion) {
        String recibido = peticion == null ? null : peticion.getHeader(CABECERA_CORRELACION);
        if (recibido != null && UUID_VALIDO.matcher(recibido.strip()).matches()) {
            return recibido.strip().toLowerCase(Locale.ROOT);
        }
        return UUID.randomUUID().toString();
    }
}
