package com.foodflow.order.application;

import java.util.UUID;

/**
 * Resultado de un pago ya validado contra el contrato, listo para aplicarse al pedido.
 *
 * <p>Lo construye {@code infrastructure.messaging} a partir del evento; el caso de uso no conoce
 * Kafka ni JSON.
 *
 * @param eventId       identificador del evento, base de la idempotencia (ADR-09)
 * @param correlationId correlacion del flujo, para los registros
 * @param orderId       pedido afectado; en el evento es tambien {@code aggregateId}
 * @param paymentId     pago que produjo el resultado
 */
public record PaymentResultCommand(UUID eventId, UUID correlationId, UUID orderId, UUID paymentId) {
}
