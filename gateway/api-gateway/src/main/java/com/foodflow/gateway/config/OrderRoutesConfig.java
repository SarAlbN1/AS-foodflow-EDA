package com.foodflow.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RequestPredicates.POST;

/**
 * Rutas de pedidos hacia Order Service (HU-401).
 *
 * <p>El gateway <b>enruta y nada mas</b> (ADR-07): no valida el cuerpo, no interpreta estados y
 * no compone respuestas. Reenvia metodo, ruta, cuerpo y cabeceras, incluida
 * {@code Idempotency-Key} sin modificar, y devuelve tal cual el codigo, las cabeceras y el cuerpo
 * de Order Service, incluidos sus errores en Problem Details.
 *
 * <p>Unica transformacion: si Order Service no responde, el gateway contesta {@code 503}
 * {@code DEPENDENCY_UNAVAILABLE} ({@link DependencyUnavailable}).
 */
@Configuration
class OrderRoutesConfig {

    @Bean
    RouterFunction<ServerResponse> orderRoutes(
            @Value("${foodflow.gateway.order-service-url}") String orderServiceUrl) {
        return route("order-service")
                .route(POST("/orders"), http())
                .route(GET("/orders/{id}"), http())
                .before(uri(orderServiceUrl))
                .onError(ResourceAccessException.class, DependencyUnavailable::response)
                .build();
    }
}
