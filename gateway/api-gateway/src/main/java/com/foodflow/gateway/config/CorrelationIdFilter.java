package com.foodflow.gateway.config;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Punto unico donde nace el {@code correlationId} (HU-403, ADR-07).
 *
 * <ul>
 *   <li>Si la solicitud trae un {@code X-Correlation-Id} con formato UUID, se conserva.</li>
 *   <li>Si falta o no es un UUID, se genera uno nuevo: el contrato
 *       ({@code contracts/api/openapi.yaml}) lo tipa como UUID, y un valor arbitrario del cliente
 *       no debe llegar a los registros ni a los eventos.</li>
 *   <li>El valor se reenvia al servicio destino en la misma cabecera, se devuelve al cliente en la
 *       respuesta y queda en el MDC ({@code correlationId}) para los registros del gateway.</li>
 * </ul>
 *
 * <p>Corre antes que cualquier otro filtro para que tambien las respuestas del propio gateway
 * ({@code 404} de ruta inexistente, {@code 503}, rechazo CORS) lleven la cabecera.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Correlation-Id";

    static final String CLAVE_MDC = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    private static final Pattern UUID_VALIDO =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibido = request.getHeader(CABECERA);
        String correlationId = resolver(recibido);
        if (recibido != null && !recibido.equals(correlationId)) {
            log.debug("X-Correlation-Id invalido reemplazado por {}", correlationId);
        }

        response.setHeader(CABECERA, correlationId);
        MDC.put(CLAVE_MDC, correlationId);
        try {
            chain.doFilter(new ConCorrelationId(request, correlationId), response);
        } finally {
            MDC.remove(CLAVE_MDC);
        }
    }

    /** Conserva el valor recibido si es un UUID; si no, genera uno. */
    static String resolver(String recibido) {
        if (recibido != null && UUID_VALIDO.matcher(recibido.strip()).matches()) {
            return recibido.strip();
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Presenta la cabecera {@code X-Correlation-Id} con el valor resuelto, de modo que el proxy la
     * reenvie al servicio destino sin duplicados ni el valor original invalido.
     */
    private static final class ConCorrelationId extends HttpServletRequestWrapper {

        private final String correlationId;

        ConCorrelationId(HttpServletRequest request, String correlationId) {
            super(request);
            this.correlationId = correlationId;
        }

        @Override
        public String getHeader(String nombre) {
            return CABECERA.equalsIgnoreCase(nombre) ? correlationId : super.getHeader(nombre);
        }

        @Override
        public Enumeration<String> getHeaders(String nombre) {
            return CABECERA.equalsIgnoreCase(nombre)
                    ? Collections.enumeration(List.of(correlationId))
                    : super.getHeaders(nombre);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> nombres = Collections.list(super.getHeaderNames());
            if (nombres.stream().noneMatch(CABECERA::equalsIgnoreCase)) {
                nombres.add(CABECERA);
            }
            return Collections.enumeration(nombres);
        }
    }
}
