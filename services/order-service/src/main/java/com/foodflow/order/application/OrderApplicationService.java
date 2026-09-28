package com.foodflow.order.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodflow.order.domain.Order;
import com.foodflow.order.infrastructure.persistence.OrderRepository;
import com.foodflow.order.validation.OrderDraft;
import com.foodflow.order.validation.OrderValidator;
import com.foodflow.order.validation.ValidatedOrderCommand;

/**
 * Casos de uso del pedido. Valida la entrada y persiste en Order DB, la unica base que
 * Order Service conoce (regla arquitectonica 3).
 *
 * <p>Aqui termina HU-101: el pedido queda en {@code CREADO} y nadie inicia el pago. La
 * publicacion de {@code OrderCreated} despues del commit la agrega HU-103, y Payment Service
 * la consume; Order Service nunca llama a Payment Service por REST (regla arquitectonica 8).
 */
@Service
public class OrderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);

    private final OrderValidator validator;
    private final OrderRepository repository;

    public OrderApplicationService(OrderValidator validator, OrderRepository repository) {
        this.validator = validator;
        this.repository = repository;
    }

    /**
     * Crea y persiste un pedido en estado {@code CREADO}.
     *
     * @throws com.foodflow.order.validation.OrderValidationException si la entrada es invalida,
     *         en cuyo caso no se escribe nada en Order DB
     */
    @Transactional
    public Order crearPedido(OrderDraft draft) {
        ValidatedOrderCommand comando = validator.validar(draft);

        Order pedido = repository.save(Order.crear(
                comando.customerReference(),
                comando.notificationChannel(),
                comando.customerContact(),
                comando.paymentToken(),
                comando.total()));

        log.info("Pedido creado orderId={} status={} total={} canal={} contacto={}",
                pedido.id(), pedido.status(), pedido.total(), pedido.notificationChannel(),
                ContactMasker.mask(pedido.customerContact()));

        return pedido;
    }
}
