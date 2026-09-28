package com.foodflow.gateway.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS del borde (HU-403): solo los origenes configurados para el entorno pueden llamar al gateway
 * desde el navegador. Un origen no listado recibe {@code 403} en el preflight y la solicitud no
 * llega a ningun servicio.
 *
 * <p>Se permiten los metodos y cabeceras de la API minima, y se exponen {@code Location} y
 * {@code X-Correlation-Id} para que Angular pueda leerlas. Sin credenciales: el prototipo no tiene
 * autenticacion.
 */
@Configuration
class CorsConfig {

    @Bean
    FilterRegistrationBean<CorsFilter> corsFilter(
            @Value("${foodflow.gateway.cors.allowed-origins}") List<String> origenesPermitidos) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origenesPermitidos.stream().map(String::strip).filter(o -> !o.isEmpty()).toList());
        cors.setAllowedMethods(List.of("GET", "POST"));
        cors.setAllowedHeaders(List.of("Content-Type", "Idempotency-Key", CorrelationIdFilter.CABECERA));
        cors.setExposedHeaders(List.of("Location", CorrelationIdFilter.CABECERA));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", cors);

        FilterRegistrationBean<CorsFilter> registro = new FilterRegistrationBean<>(new CorsFilter(fuente));
        // Despues de CorrelationIdFilter, para que el rechazo CORS tambien lleve la cabecera.
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registro;
    }
}
