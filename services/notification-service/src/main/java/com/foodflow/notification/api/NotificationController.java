package com.foodflow.notification.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.foodflow.notification.application.NotificationQueryService;

/**
 * {@code GET /orders/{id}/notifications} (HU-305). El API Gateway enruta aqui solo esta
 * operacion (HU-402); el resto de {@code /orders} va a Order Service.
 *
 * <p>Siempre {@code 200} con una lista, posiblemente vacia: Notification Service no conoce el
 * catalogo de pedidos, asi que un pedido desconocido y uno cuyo pago aun no se resolvio se ven
 * igual. Un identificador que no es UUID es {@code 400} en Problem Details
 * ({@link ApiExceptionHandler}).
 */
@RestController
public class NotificationController {

    private final NotificationQueryService consultas;

    public NotificationController(NotificationQueryService consultas) {
        this.consultas = consultas;
    }

    @GetMapping("/orders/{id}/notifications")
    public List<NotificationResponse> notificacionesDelPedido(@PathVariable UUID id) {
        return consultas.notificacionesDelPedido(id).stream()
                .map(NotificationResponse::from)
                .toList();
    }
}
