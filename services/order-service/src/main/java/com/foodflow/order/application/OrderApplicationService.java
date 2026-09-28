package com.foodflow.order.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodflow.order.domain.Order;
import com.foodflow.order.infrastructure.messaging.OrderEventPublisher;
import com.foodflow.order.infrastructure.persistence.OrderRepository;
import com.foodflow.order.validation.OrderDraft;
import com.foodflow.order.validation.OrderValidator;
import com.foodflow.order.validation.ValidatedOrderCommand;

/**
 * Casos de uso del pedido. Valida la entrada y persiste en Order DB, la unica base que
 * Order Service conoce (regla arquitectonica 3).
 *
 * <p>El pedido queda en {@code CREADO} y este servicio no inicia el pago: publica
 * {@code OrderCreated} despues del commit y Payment Service lo consume (HU-103). Order Service
 * nunca llama a Payment Service por REST (reglas arquitectonicas 4 y 5).
 */
@Service
public class OrderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);

    private final OrderValidator validator;
    private final OrderRepository repository;
    private final OrderEventPublisher publicador;

    public OrderApplicationService(OrderValidator validator, OrderRepository repository,
            OrderEventPublisher publicador) {
        this.validator = validator;
        this.repository = repository;
        this.publicador = publicador;
    }

    /**
     * Crea y persiste un pedido en estado {@code CREADO} y publica {@code OrderCreated}.
     *
     * <p>El evento se envia <strong>despues</strong> del commit de esta transaccion (HU-103,
     * criterios 1 y 3): si la persistencia falla, no se publica nada. Lo registra
     * {@link OrderEventPublisher}, que es quien conoce Kafka.
     *
     * @param correlationId correlacion de extremo a extremo que entra por el gateway y viaja en
     *                      el envelope del evento
     * @throws com.foodflow.order.validation.OrderValidationException si la entrada es invalida,
     *         en cuyo caso no se escribe nada en Order DB ni se publica ningun evento
     */
    @Transactional
    public Order crearPedido(OrderDraft draft, UUID correlationId) {
        ValidatedOrderCommand comando = validator.validar(draft);

        Order pedido = repository.save(Order.crear(
                comando.customerReference(),
                comando.notificationChannel(),
                comando.customerContact(),
                comando.paymentToken(),
                comando.total()));

        log.info("Pedido creado orderId={} status={} total={} canal={} correlationId={} contacto={}",
                pedido.id(), pedido.status(), pedido.total(), pedido.notificationChannel(),
                correlationId, ContactMasker.mask(pedido.customerContact()));

        publicador.publicarOrderCreated(pedido, correlationId);

        return pedido;
    }

    /**
     * Devuelve el pedido tal como esta persistido en Order DB (criterios 1 y 2 de HU-102).
     *
     * <p>El estado se lee de la base en cada consulta: no se reconstruye preguntando a Payment
     * Service, a Notification Service ni a Kafka (criterio 4 de HU-102 y regla arquitectonica 8).
     *
     * @throws OrderNotFoundException si no existe un pedido con ese identificador
     */
    @Transactional(readOnly = true)
    public Order consultarPedido(UUID id) {
        return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }
}
