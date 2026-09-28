package com.foodflow.payment.domain;

/**
 * Canal por el que se notifica al cliente. El prototipo solo implementa correo electronico
 * ({@code contracts/events/v1/envelope.schema.json}).
 *
 * <p>Payment Service no notifica: solo transporta el snapshot de contacto que recibio en
 * {@code OrderCreated} hasta el evento de pago (ADR-11, supuesto A-2). Solo Notification
 * Service llama al proveedor externo (regla arquitectonica 7).
 */
public enum NotificationChannel {

    EMAIL
}
