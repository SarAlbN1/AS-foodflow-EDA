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
 * Ruta de la consulta de notificaciones hacia Notification Service (HU-402).
 *
 * <p>{@code GET /orders/{id}/notifications} va <b>unicamente</b> a Notification Service, que es
 * el propietario de Notification DB: el gateway no consulta a Order Service para validar el
 * pedido ni combina datos de los dos servicios (ADR-07). Un pedido sin notificaciones o
 * inexistente responde {@code []}, porque Notification Service no conoce el catalogo de pedidos
 * ({@code contracts/api/openapi.yaml}); el gateway lo devuelve tal cual.
 *
 * <p>La ruta de pedidos {@code GET /orders/{id}} solo cubre un segmento despues de
 * {@code /orders}, asi que no captura esta. Si Notification Service no responde, la unica
 * respuesta propia del gateway es {@code 503 DEPENDENCY_UNAVAILABLE}, igual que en pedidos.
 */
@Configuration
class NotificationRoutesConfig {

    @Bean
    RouterFunction<ServerResponse> notificationRoutes(
            @Value("${foodflow.gateway.notification-service-url}") String notificationServiceUrl) {
        return route("notification-service")
                .route(GET("/orders/{id}/notifications"), http())
                .before(uri(notificationServiceUrl))
                .onError(ResourceAccessException.class, DependencyUnavailable::response)
                .build();
    }
}
