package com.foodflow.notification.domain;

/**
 * Canal por el que se notifica al cliente. El prototipo solo implementa correo electronico
 * ({@code contracts/events/v1/envelope.schema.json}).
 *
 * <p>Es una copia propia del concepto: ningun servicio depende de clases de otro
 * (regla arquitectonica 8).
 */
public enum NotificationChannel {

    EMAIL
}
