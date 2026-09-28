package com.foodflow.order.api;

import java.net.URI;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
 * todavia no tiene efecto; lo implementa HU-107.
 */
@RestController
@RequestMapping("/orders")
class OrderController {

    /** Nombre de la cabecera de idempotencia, tal como la documenta el contrato. */
    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    /** Longitud maxima que admite el contrato y la columna {@code idempotency_keys.key}. */
    private static final int LARGO_MAXIMO = 200;

    private final OrderApplicationService servicio;

    OrderController(OrderApplicationService servicio) {
        this.servicio = servicio;
    }

    /**
     * Crea el pedido y responde {@code 201} con {@code Location} hacia el recurso creado.
     *
     * <p>El {@code correlationId} de la peticion viaja al envelope de {@code OrderCreated}, que
     * se publica despues del commit (HU-103).
     *
     * <p>La cabecera {@code Idempotency-Key} es obligatoria ({@code contracts/api/openapi.yaml}):
     * reenviar la misma solicitud devuelve el pedido original en lugar de crear otro (HU-107).
     * La cabecera se declara opcional en la firma para poder responder {@code 400} en Problem
     * Details en vez del error generico que produciria Spring al faltar una cabecera requerida.
     */
    @PostMapping
    ResponseEntity<OrderResponse> crear(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody CreateOrderRequest request,
            HttpServletRequest peticion) {
        Order pedido = servicio.crearPedido(
                request.aBorrador(), CorrelationId.de(peticion), exigirClave(idempotencyKey));
        return ResponseEntity
                .created(URI.create("/orders/" + pedido.id()))
                .body(OrderResponse.from(pedido));
    }

    /**
     * Consulta el pedido por su identificador y responde {@code 200} con la misma representacion
     * que {@code POST /orders}. Un identificador inexistente produce {@code 404} y uno que no es
     * UUID, {@code 400} (contrato {@code contracts/api/openapi.yaml}).
     */
    @GetMapping("/{id}")
    ResponseEntity<OrderResponse> consultar(@PathVariable UUID id) {
        return ResponseEntity.ok(OrderResponse.from(servicio.consultarPedido(id)));
    }

    /** Comprueba la cabecera antes de tocar nada: sin ella la solicitud no se procesa. */
    private static String exigirClave(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException("es obligatoria");
        }
        String clave = idempotencyKey.strip();
        if (clave.length() > LARGO_MAXIMO) {
            throw new MissingIdempotencyKeyException("admite como maximo " + LARGO_MAXIMO + " caracteres");
        }
        return clave;
    }
}
