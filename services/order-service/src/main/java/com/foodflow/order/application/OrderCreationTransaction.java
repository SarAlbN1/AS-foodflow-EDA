package com.foodflow.order.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.foodflow.order.domain.IdempotencyKey;
import com.foodflow.order.domain.Order;
import com.foodflow.order.infrastructure.messaging.OrderEventPublisher;
import com.foodflow.order.infrastructure.persistence.IdempotencyKeyRepository;
import com.foodflow.order.infrastructure.persistence.OrderRepository;
import com.foodflow.order.validation.ValidatedOrderCommand;

/**
 * Las escrituras de la creacion de un pedido, en una sola transaccion local.
 *
 * <p><strong>Por que es un componente aparte.</strong> Spring aplica {@code @Transactional}
 * mediante un proxy, asi que una llamada entre metodos del mismo objeto no abriria transaccion.
 * {@link OrderApplicationService} necesita justamente eso: reaccionar <em>fuera</em> de la
 * transaccion cuando dos solicitudes con la misma clave llegan a la vez y una pierde la carrera.
 * Separarlo es lo que hace que ese reintento ocurra sobre una transaccion ya deshecha y no
 * sobre una marcada para descarte.
 */
@Component
public class OrderCreationTransaction {

    private final OrderRepository pedidos;
    private final IdempotencyKeyRepository claves;
    private final OrderEventPublisher publicador;

    public OrderCreationTransaction(OrderRepository pedidos, IdempotencyKeyRepository claves,
            OrderEventPublisher publicador) {
        this.pedidos = pedidos;
        this.claves = claves;
        this.publicador = publicador;
    }

    /** Busca la clave sin abrir escritura. Vacio si esta solicitud no se ha atendido todavia. */
    @Transactional(readOnly = true)
    public Optional<IdempotencyKey> buscarClave(String key) {
        return claves.findById(key);
    }

    /** Devuelve el pedido que produjo una clave ya registrada. */
    @Transactional(readOnly = true)
    public Optional<Order> buscarPedido(UUID orderId) {
        return pedidos.findById(orderId);
    }

    /**
     * Persiste el pedido y su clave de idempotencia, y deja registrada la publicacion de
     * {@code OrderCreated} para despues del commit (HU-103).
     *
     * <p>Las dos escrituras van en la misma transaccion: no puede quedar un pedido sin su clave
     * ni una clave apuntando a un pedido que no existe. Si otra solicitud con la misma clave
     * gana la carrera, la clave primaria de {@code idempotency_keys} hace fallar esta y la
     * transaccion entera se deshace, incluido el pedido.
     */
    @Transactional
    public Order crear(ValidatedOrderCommand comando, String requestHash, String key, UUID correlationId) {
        Order pedido = pedidos.save(Order.crear(
                comando.customerReference(),
                comando.notificationChannel(),
                comando.customerContact(),
                comando.paymentToken(),
                comando.total()));

        claves.save(IdempotencyKey.de(key, requestHash, pedido.id()));

        publicador.publicarOrderCreated(pedido, correlationId);
        return pedido;
    }
}
