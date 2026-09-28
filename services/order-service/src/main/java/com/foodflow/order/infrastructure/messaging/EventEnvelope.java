package com.foodflow.order.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope comun de los eventos de FoodFlow
 * ({@code contracts/events/v1/envelope.schema.json}).
 *
 * <p>Es una copia propia del contrato: ningun servicio depende de clases Java de otro
 * (regla arquitectonica 8).
 *
 * <p>{@code aggregateId} es siempre el {@code orderId} y es la clave de particion del
 * mensaje (ADR-04), de modo que todos los eventos de un pedido conservan su orden.
 *
 * @param <P> tipo del payload, distinto para cada {@code eventType}
 */
public record EventEnvelope<P>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID correlationId,
        UUID aggregateId,
        P payload) {

    /** Version del contrato que publica este servicio ({@code contracts/events/v1}). */
    public static final int VERSION = 1;

    /**
     * Envuelve un payload con un {@code eventId} nuevo y el instante actual en UTC.
     *
     * <p>El {@code eventId} es lo que sustenta la idempotencia de los consumidores (ADR-09):
     * se genera una sola vez por hecho, no por intento de publicacion.
     */
    public static <P> EventEnvelope<P> de(String eventType, UUID aggregateId, UUID correlationId, P payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                VERSION,
                Instant.now(),
                correlationId,
                aggregateId,
                payload);
    }
}
