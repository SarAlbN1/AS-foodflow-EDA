package com.foodflow.order.api;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.foodflow.order.application.OrderApplicationService;
import com.foodflow.order.domain.Order;

/**
 * Operaciones HTTP de pedidos. El cliente llega siempre a traves del API Gateway
 * (reglas arquitectonicas 1 y 2) y <b>solo crea el pedido</b>: no dispara el pago.
 *
 * <p>El encabezado {@code Idempotency-Key} de la pagina {@code docs/wiki/03-contratos/api-rest.md}
 * todavia no tiene efecto; lo implementa HU-107. {@code GET /orders/&#123;id&#125;} lo agrega HU-102.
 */
@RestController
@RequestMapping("/orders")
class OrderController {

    private final OrderApplicationService servicio;

    OrderController(OrderApplicationService servicio) {
        this.servicio = servicio;
    }

    /** Crea el pedido y responde {@code 201} con {@code Location} hacia el recurso creado. */
    @PostMapping
    ResponseEntity<OrderResponse> crear(@RequestBody CreateOrderRequest request) {
        Order pedido = servicio.crearPedido(request.aBorrador());
        return ResponseEntity
                .created(URI.create("/orders/" + pedido.id()))
                .body(OrderResponse.from(pedido));
    }
}
