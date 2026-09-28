package com.foodflow.payment.domain;

/**
 * Snapshot del contacto y del canal capturado al crear el pedido (ADR-11).
 *
 * <p>Payment Service lo copia de {@code OrderCreated} a {@code PaymentApproved} o
 * {@code PaymentRejected} sin interpretarlo, para que Notification Service no dependa de
 * {@code OrderStatusChanged} (supuesto A-2). No se persiste en Payment DB: la tabla
 * {@code payments} no tiene columnas de contacto, asi que solo viaja en el evento.
 *
 * <p>{@code destination} es dato personal: se registra enmascarado
 * ({@code docs/wiki/04-implementacion/convenciones.md}).
 */
public record NotificationContact(NotificationChannel channel, String destination) {

    public NotificationContact {
        if (channel == null) {
            throw new IllegalArgumentException("notificationContact.channel es obligatorio");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("notificationContact.destination es obligatorio");
        }
    }
}
