package com.foodflow.order.infrastructure.messaging;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.foodflow.order.config.EventJsonConfig;
import com.foodflow.order.domain.Order;
import com.foodflow.order.domain.OrderStatus;

import tools.jackson.databind.ObjectMapper;

/**
 * Publica los eventos de Order Service en {@code orders.events} (HU-103).
 *
 * <p>Es el unico punto del servicio que habla con Kafka
 * ({@code docs/wiki/04-implementacion/convenciones.md}). Order Service no llama a Payment
 * Service: publica el hecho y Payment lo consume (reglas arquitectonicas 4 y 5).
 *
 * <p><strong>Despues del commit (ADR-08), y sin bloquear la respuesta.</strong> La publicacion
 * se registra para ejecutarse cuando la transaccion que persiste el pedido ya ha hecho commit,
 * asi que un pedido que no llega a guardarse nunca produce evento. No se espera la
 * confirmacion del broker: eso ocurriria en el hilo de la peticion HTTP y acoplaria el tiempo
 * de respuesta de {@code POST /orders} al de Kafka. La contrapartida es la ventana de escritura dual que
 * ADR-08 acepta: si el commit sale bien y la publicacion falla, el pedido queda en
 * {@code CREADO} sin evento. Se registra el fallo y no se hace nada mas: no hay Outbox ni
 * tarea de reconciliacion, y esa es la decision, no un olvido.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    /** Nombre del evento en el catalogo. Nunca {@code OrderUpdated}. */
    static final String ORDER_CREATED = "OrderCreated";
    static final String ORDER_STATUS_CHANGED = "OrderStatusChanged";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper jackson;
    private final String ordersTopic;

    public OrderEventPublisher(
            KafkaTemplate<String, String> kafka,
            @Qualifier(EventJsonConfig.EVENT_OBJECT_MAPPER) ObjectMapper jackson,
            @Value("${foodflow.kafka.orders-topic}") String ordersTopic) {
        this.kafka = kafka;
        this.jackson = jackson;
        this.ordersTopic = ordersTopic;
    }

    /**
     * Publica {@code OrderCreated} para un pedido ya persistido.
     *
     * <p>Si hay una transaccion activa, la publicacion espera a su commit; si no la hay, se
     * envia de inmediato. Llamar a este metodo nunca hace fallar la creacion del pedido: un
     * error de publicacion se registra y se traga, porque el pedido ya esta guardado y
     * propagar la excepcion solo conseguiria devolver un error por algo que si ocurrio.
     */
    public void publicarOrderCreated(Order pedido, UUID correlationId) {
        publicarTrasElCommit(EventEnvelope.de(
                ORDER_CREATED, pedido.id(), correlationId, OrderCreatedPayload.de(pedido)));
    }

    /**
     * Publica {@code OrderStatusChanged} (HU-106) con el mismo patron que {@code OrderCreated}:
     * despues del commit de la transaccion que cambio el estado, nunca antes (ADR-08). Conserva
     * el {@code correlationId} del flujo, que llega en el evento de pago.
     *
     * @param anterior estado del que partio el pedido; hoy siempre {@code CREADO}
     */
    public void publicarOrderStatusChanged(Order pedido, OrderStatus anterior, UUID correlationId) {
        publicarTrasElCommit(EventEnvelope.de(
                ORDER_STATUS_CHANGED, pedido.id(), correlationId, OrderStatusChangedPayload.de(pedido, anterior)));
    }

    /**
     * Si hay una transaccion en curso, el envio se registra para {@code afterCommit}: un rollback
     * no deja un evento publicado de un cambio que no ocurrio. Sin transaccion, se envia ya.
     */
    private void publicarTrasElCommit(EventEnvelope<?> evento) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar(evento);
                }
            });
        } else {
            enviar(evento);
        }
    }

    private void enviar(EventEnvelope<?> evento) {
        // La clave es el orderId: es la clave de particion (regla 11, ADR-04), lo que
        // garantiza que todos los eventos de un pedido van a la misma particion y en orden.
        String clave = evento.aggregateId().toString();
        try {
            kafka.send(ordersTopic, clave, jackson.writeValueAsString(evento))
                    .whenComplete((resultado, fallo) -> {
                        try (var correlation = MDC.putCloseable("correlationId", evento.correlationId().toString());
                                var eventId = MDC.putCloseable("eventId", evento.eventId().toString());
                                var eventType = MDC.putCloseable("eventType", evento.eventType());
                                var orderId = MDC.putCloseable("orderId", evento.aggregateId().toString())) {
                            if (fallo == null) {
                                log.info("Evento publicado eventType={} eventId={} orderId={} correlationId={} topic={}",
                                        evento.eventType(), evento.eventId(), evento.aggregateId(),
                                        evento.correlationId(), ordersTopic);
                            } else {
                                registrarFallo(evento, fallo);
                            }
                        }
                    });
        } catch (Exception e) {
            // Un fallo sincrono de send: serializacion, o metadatos no disponibles al agotarse
            // max.block.ms con el broker caido.
            try (var correlation = MDC.putCloseable("correlationId", evento.correlationId().toString());
                    var eventId = MDC.putCloseable("eventId", evento.eventId().toString());
                    var eventType = MDC.putCloseable("eventType", evento.eventType());
                    var orderId = MDC.putCloseable("orderId", evento.aggregateId().toString())) {
                registrarFallo(evento, e);
            }
        }
    }

    /** CA5 y ADR-08: el pedido permanece en {@code CREADO} y nadie lo reconcilia. */
    private void registrarFallo(EventEnvelope<?> evento, Throwable causa) {
        // Sin Outbox (ADR-08): el cambio ya esta en Order DB y el evento se pierde.
        log.error("No se pudo publicar {} tras el commit. El cambio queda persistido sin su evento. "
                        + "eventId={} orderId={} correlationId={} topic={} causa={}",
                evento.eventType(), evento.eventId(), evento.aggregateId(),
                evento.correlationId(), ordersTopic, causa.toString());
    }
}
