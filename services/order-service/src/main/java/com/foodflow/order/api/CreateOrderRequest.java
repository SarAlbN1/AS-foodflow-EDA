package com.foodflow.order.api;

import java.math.BigDecimal;

import com.foodflow.order.validation.OrderDraft;

/**
 * Cuerpo de {@code POST /orders} (pagina {@code docs/wiki/03-contratos/api-rest.md}).
 *
 * <p>No lleva anotaciones de validacion: las reglas del criterio 3 de HU-101 viven en
 * {@code OrderValidator}, de modo que exista una sola fuente de verdad para el {@code 400}
 * y para su mensaje.
 */
public record CreateOrderRequest(
        String customerReference,
        String customerContact,
        String notificationChannel,
        BigDecimal total,
        String paymentToken) {

    OrderDraft aBorrador() {
        return new OrderDraft(customerReference, customerContact, notificationChannel, total, paymentToken);
    }
}
