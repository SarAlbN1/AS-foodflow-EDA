package com.foodflow.order.application;

import java.util.Optional;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodflow.order.domain.Order;
import com.foodflow.order.domain.OrderStatus;
import com.foodflow.order.domain.ProcessedEvent;
import com.foodflow.order.infrastructure.messaging.OrderEventPublisher;
import com.foodflow.order.infrastructure.persistence.OrderRepository;
import com.foodflow.order.infrastructure.persistence.ProcessedEventRepository;

/**
 * Aplica al pedido el resultado del pago que publica Payment Service (HU-104 y HU-105).
 *
 * <p>Order Service reacciona al pago por su cuenta, en paralelo con Notification Service (D-6,
 * reglas 6 y 12): ninguno de los dos espera al otro.
 *
 * <p><strong>Un evento, un efecto (ADR-09).</strong> El {@code eventId} se registra en
 * {@code processed_events} en la misma transaccion local que el cambio de estado. Si el evento ya
 * estaba registrado se ignora con {@code INFO}: una reentrega de Kafka no produce un segundo
 * cambio ni un error (criterio 3).
 *
 * <p><strong>Pedido inexistente.</strong> Es un error recuperable ({@code comportamiento-del-flujo.md}):
 * se lanza para que el consumidor no confirme el offset y Kafka reintente; si persiste, HU-602 lo
 * lleva a la DLQ. No se registra el {@code eventId}, porque no hubo efecto.
 *
 * <p><strong>{@code OrderStatusChanged} (HU-106).</strong> Cada transicion valida lo publica en
 * {@code orders.events} despues del commit, con el {@code correlationId} del evento de pago. Una
 * transicion invalida, un evento repetido o un pedido inexistente no publican nada.
 */
@Service
public class OrderPaymentService {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentService.class);

    /** Identifica a este consumidor en {@code processed_events}. */
    static final String CONSUMIDOR = "order-service.payments";

    private final OrderRepository pedidos;
    private final ProcessedEventRepository procesados;
    private final OrderEventPublisher publicador;

    public OrderPaymentService(OrderRepository pedidos, ProcessedEventRepository procesados,
            OrderEventPublisher publicador) {
        this.pedidos = pedidos;
        this.procesados = procesados;
        this.publicador = publicador;
    }

    /**
     * Pago aprobado: el pedido pasa de {@code CREADO} a {@code PAGADO}.
     *
     * <p>Si el pedido ya no esta en {@code CREADO}, la transicion es invalida: se ignora con
     * {@code WARN}, sin error, y el {@code eventId} queda registrado igualmente para no volver a
     * evaluarla en una reentrega.
     *
     * @return el pedido tras aplicar el evento, o vacio si el evento ya se habia procesado
     * @throws OrderNotFoundException si el pedido no existe en Order DB (recuperable)
     */
    @Transactional
    public Optional<Order> registrarPagoAprobado(PaymentResultCommand resultado) {
        return aplicar(resultado, Order::marcarPagado, "aprobado");
    }

    /**
     * Pago rechazado: el pedido pasa de {@code CREADO} a {@code PAGO_RECHAZADO} (HU-105). Mismas
     * reglas que {@link #registrarPagoAprobado}.
     *
     * @return el pedido tras aplicar el evento, o vacio si el evento ya se habia procesado
     * @throws OrderNotFoundException si el pedido no existe en Order DB (recuperable)
     */
    @Transactional
    public Optional<Order> registrarPagoRechazado(PaymentResultCommand resultado) {
        return aplicar(resultado, Order::marcarPagoRechazado, "rechazado");
    }

    /** Idempotencia, busqueda del pedido y transicion: lo comun a los dos resultados del pago. */
    private Optional<Order> aplicar(PaymentResultCommand resultado, Predicate<Order> transicion, String resultadoPago) {
        if (procesados.existsById(resultado.eventId())) {
            log.info("Evento ya procesado, no se vuelve a aplicar. eventId={} orderId={} correlationId={}",
                    resultado.eventId(), resultado.orderId(), resultado.correlationId());
            return Optional.empty();
        }

        Order pedido = pedidos.findById(resultado.orderId())
                .orElseThrow(() -> new OrderNotFoundException(resultado.orderId()));

        OrderStatus anterior = pedido.status();
        if (transicion.test(pedido)) {
            // Se registra para despues del commit: si la transaccion se deshace, no sale.
            publicador.publicarOrderStatusChanged(pedido, anterior, resultado.correlationId());
            log.info("Pedido {} orderId={} paymentId={} eventId={} correlationId={}",
                    pedido.status(), pedido.id(), resultado.paymentId(), resultado.eventId(),
                    resultado.correlationId());
        } else {
            log.warn("Transicion invalida ignorada: el pago {} llega con el pedido en {} "
                            + "orderId={} paymentId={} eventId={} correlationId={}",
                    resultadoPago, pedido.status(), pedido.id(), resultado.paymentId(), resultado.eventId(),
                    resultado.correlationId());
        }

        // En la misma transaccion: si esto falla, el cambio de estado tampoco queda.
        procesados.saveAndFlush(ProcessedEvent.de(resultado.eventId(), CONSUMIDOR));
        return Optional.of(pedido);
    }
}
