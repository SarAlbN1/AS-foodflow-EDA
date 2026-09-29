package com.foodflow.notification.domain;

/**
 * Estado de entrega de una notificacion
 * ({@code docs/wiki/03-contratos/persistencia.md}).
 *
 * <p>Transiciones permitidas: {@link #PENDIENTE} a {@link #ENVIADA} y {@link #PENDIENTE} a
 * {@link #FALLIDA}. Cualquier otra se ignora con {@code WARN}, sin error y sin evento
 * ({@code docs/wiki/02-arquitectura/comportamiento-del-flujo.md}).
 *
 * <p>Los estados de negocio se nombran en espanol.
 */
public enum NotificationStatus {

    PENDIENTE,

    ENVIADA,

    FALLIDA
}
