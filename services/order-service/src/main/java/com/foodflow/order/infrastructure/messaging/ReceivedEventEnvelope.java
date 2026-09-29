package com.foodflow.order.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * Envelope comun de un evento recibido ({@code contracts/events/v1/envelope.schema.json}).
 *
 * <p>Es la contraparte de lectura de {@link EventEnvelope}: el payload queda como arbol JSON
 * hasta saber el {@code eventType}, y los campos son nullables para poder rechazar con un motivo
 * claro un envelope incompleto en lugar de fallar al deserializar.
 */
public record ReceivedEventEnvelope(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        Instant occurredAt,
        UUID correlationId,
        UUID aggregateId,
        JsonNode payload) {
}
