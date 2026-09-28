package com.foodflow.payment.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

/**
 * Envelope comun de los eventos de FoodFlow
 * ({@code contracts/events/v1/envelope.schema.json}).
 *
 * <p>Es una copia propia del contrato: ningun servicio depende de clases Java de otro
 * (regla arquitectonica 8).
 *
 * <p>El tipo del payload es un parametro porque el envelope se usa en los dos sentidos. Al
 * <strong>leer</strong> se enlaza como {@code EventEnvelope<JsonNode>}, porque la forma del
 * payload depende del {@code eventType} y este solo se conoce tras leer el envelope: asi
 * {@code orders.events} puede transportar tipos que Payment Service ignora sin intentar
 * enlazarlos. Al <strong>publicar</strong> se usa el record concreto del evento.
 *
 * <p>{@code aggregateId} es siempre el {@code orderId} y es la clave de particion del mensaje
 * (ADR-04), de modo que todos los eventos de un pedido conservan su orden.
 *
 * @param <P> tipo del payload, distinto para cada {@code eventType}
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record EventEnvelope<P>(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        Instant occurredAt,
        UUID correlationId,
        UUID aggregateId,
        P payload) {

    /** Version del contrato que produce y consume este servicio ({@code contracts/events/v1}). */
    public static final int VERSION = 1;

    /**
     * Envuelve un payload con un {@code eventId} nuevo y el instante actual en UTC.
     *
     * <p>El {@code eventId} es lo que sustenta la idempotencia de los consumidores (ADR-09):
     * se genera una sola vez por hecho, no por intento de publicacion.
     *
     * @param correlationId la correlacion del evento que origino este hecho, para que el
     *                      recorrido del pedido siga siendo rastreable de extremo a extremo
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
