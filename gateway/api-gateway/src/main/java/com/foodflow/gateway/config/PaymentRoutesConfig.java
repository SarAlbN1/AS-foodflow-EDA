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

/**
 * Ruta de la consulta del pago hacia Payment Service (HU-205, opcional).
 *
 * <p>{@code GET /orders/{id}/payment} va <b>unicamente</b> a Payment Service, propietario de
 * Payment DB, con el mismo patron que la consulta de notificaciones: el gateway no consulta a
 * Order Service ni combina datos (ADR-07). Si todavia no hay pago, Payment Service responde
 * {@code 404 NOT_FOUND} y el gateway lo devuelve tal cual.
 *
 * <p>La ruta de pedidos {@code GET /orders/{id}} solo cubre un segmento despues de
 * {@code /orders}, asi que no captura esta. Si Payment Service no responde, el gateway responde
 * {@code 503 DEPENDENCY_UNAVAILABLE}, igual que en las demas rutas.
 */
@Configuration
class PaymentRoutesConfig {

    @Bean
    RouterFunction<ServerResponse> paymentRoutes(
            @Value("${foodflow.gateway.payment-service-url}") String paymentServiceUrl) {
        return route("payment-service")
                .route(GET("/orders/{id}/payment"), http())
                .before(uri(paymentServiceUrl))
                .onError(ResourceAccessException.class, DependencyUnavailable::response)
                .build();
    }
}
