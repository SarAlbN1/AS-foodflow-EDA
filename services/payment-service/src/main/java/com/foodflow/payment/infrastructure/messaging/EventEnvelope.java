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
 * (regla arquitectonica 8). El {@code payload} se deja como arbol JSON porque su forma
 * depende del {@code eventType}, que solo se conoce tras leer el envelope: asi
 * {@code orders.events} puede transportar tipos que Payment Service ignora sin intentar
 * enlazarlos.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record EventEnvelope(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        Instant occurredAt,
        UUID correlationId,
        UUID aggregateId,
        JsonNode payload) {
}
