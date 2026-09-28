package com.foodflow.order.domain;

/**
 * Estados del pedido. El catalogo y las transiciones validas estan en
 * {@code docs/wiki/02-arquitectura/comportamiento-del-flujo.md} y los replica la
 * restriccion {@code orders_status_valido} de Order DB.
 */
public enum OrderStatus {

    /** Estado inicial: el pedido se persistio y todavia no hay resultado de pago (HU-101). */
    CREADO,

    /** El pago fue aprobado (HU-104). */
    PAGADO,

    /** El pago fue rechazado (HU-105). */
    PAGO_RECHAZADO
}
