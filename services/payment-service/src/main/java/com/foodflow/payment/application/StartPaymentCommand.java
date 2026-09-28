package com.foodflow.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.foodflow.payment.domain.NotificationContact;
import com.foodflow.payment.domain.PaymentToken;

/**
 * Orden de iniciar el pago de un pedido, ya validada contra el contrato de
 * {@code OrderCreated}. Es la frontera entre {@code infrastructure.messaging}, que sabe de
 * Kafka y de JSON, y la aplicacion, que no.
 *
 * <p>Todo lo que contiene llega en el evento: Payment Service nunca consulta Order DB ni
 * llama por REST a Order Service (reglas arquitectonicas 2 y 4).
 *
 * @param eventId       identificador del evento consumido; sustenta la idempotencia (ADR-09)
 * @param correlationId correlacion de extremo a extremo que se propaga al evento de pago
 * @param orderId       pedido a cobrar; coincide con el {@code aggregateId} del envelope
 * @param amount        total del pedido, con escala 2
 * @param currency      moneda del prototipo, siempre {@code COP}
 * @param paymentToken  resultado determinista del pago (ADR-10)
 * @param contact       snapshot de contacto que se copiara al evento de pago (ADR-11)
 */
public record StartPaymentCommand(
        UUID eventId,
        UUID correlationId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        PaymentToken paymentToken,
        NotificationContact contact) {
}
