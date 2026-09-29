package com.foodflow.order.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodflow.order.domain.IdempotencyKey;
import com.foodflow.order.domain.Order;
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
    private final OrderCreationTransaction transaccion;

    public OrderApplicationService(OrderValidator validator, OrderRepository repository,
            OrderCreationTransaction transaccion) {
        this.validator = validator;
        this.repository = repository;
        this.transaccion = transaccion;
    }

    /**
     * Crea el pedido, o devuelve el que ya produjo esta misma solicitud (HU-107).
     *
     * <p>Este metodo <strong>no</strong> es transaccional a proposito: la escritura vive en
     * {@link OrderCreationTransaction} para poder reaccionar aqui, ya fuera de la transaccion,
     * cuando dos solicitudes con la misma clave llegan a la vez.
     *
     * <table>
     *   <caption>Que ocurre segun la clave y el cuerpo</caption>
     *   <tr><td>Clave nueva</td><td>Se crea el pedido y se publica {@code OrderCreated}</td></tr>
     *   <tr><td>Misma clave, mismo cuerpo</td><td>Se devuelve el pedido original; no se crea
     *       otro ni se publica otro evento (criterio 2)</td></tr>
     *   <tr><td>Misma clave, cuerpo distinto</td><td>{@link IdempotencyConflictException},
     *       que la capa {@code api} traduce a {@code 409} (criterio 3)</td></tr>
     * </table>
     *
     * @param key clave de idempotencia de la solicitud; el contrato la exige siempre
     * @throws com.foodflow.order.validation.OrderValidationException si la entrada es invalida,
     *         en cuyo caso no se escribe nada en Order DB ni se publica ningun evento
     */
    public Order crearPedido(OrderDraft draft, UUID correlationId, String key) {
        ValidatedOrderCommand comando = validator.validar(draft);
        String requestHash = RequestHash.de(comando);

        Order yaCreado = pedidoDeClaveExistente(key, requestHash);
        if (yaCreado != null) {
            return yaCreado;
        }

        try (var correlation = MDC.putCloseable("correlationId", correlationId.toString())) {
            try {
                Order pedido = transaccion.crear(comando, requestHash, key, correlationId);
                try (var orderId = MDC.putCloseable("orderId", pedido.id().toString())) {
                    log.info("Pedido creado orderId={} status={} total={} canal={} correlationId={} contacto={}",
                            pedido.id(), pedido.status(), pedido.total(), pedido.notificationChannel(),
                            correlationId, ContactMasker.mask(pedido.customerContact()));
                }
                return pedido;
            } catch (DataIntegrityViolationException e) {
                // Otra solicitud con la misma clave gano la carrera y escribio primero. La clave
                // primaria de idempotency_keys hizo su trabajo: esta transaccion se deshizo entera,
                // asi que no quedo ningun pedido a medias. Se relee ya fuera de ella.
                Order delOtro = pedidoDeClaveExistente(key, requestHash);
                if (delOtro == null) {
                    throw e;
                }
                try (var orderId = MDC.putCloseable("orderId", delOtro.id().toString())) {
                    log.info("Solicitud simultanea con la misma Idempotency-Key: se devuelve el pedido que gano. "
                            + "orderId={} correlationId={}", delOtro.id(), correlationId);
                }
                return delOtro;
            }
        }
    }

    /**
     * Pedido que ya produjo esta clave, o {@code null} si la clave no se ha usado.
     *
     * @throws IdempotencyConflictException si la clave existe con otro cuerpo
     */
    private Order pedidoDeClaveExistente(String key, String requestHash) {
        IdempotencyKey registrada = transaccion.buscarClave(key).orElse(null);
        if (registrada == null) {
            return null;
        }
        if (!registrada.mismaSolicitud(requestHash)) {
            throw new IdempotencyConflictException(key);
        }
        // El pedido tiene que existir: la clave lo referencia por clave foranea.
        return transaccion.buscarPedido(registrada.orderId())
                .orElseThrow(() -> new OrderNotFoundException(registrada.orderId()));
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
