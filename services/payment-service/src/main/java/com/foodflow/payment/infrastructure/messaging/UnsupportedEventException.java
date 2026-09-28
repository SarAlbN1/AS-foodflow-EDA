package com.foodflow.payment.infrastructure.messaging;

/**
 * Un evento de {@code orders.events} no se puede procesar y reintentarlo no lo arreglaria:
 * JSON ilegible, envelope incompleto, version de contrato no soportada o payload que no
 * cumple {@code contracts/events/v1/order-created.schema.json}.
 *
 * <p>Es el fallo "tecnico no recuperable" de
 * {@code docs/wiki/02-arquitectura/comportamiento-del-flujo.md}, cuyo destino es
 * {@code orders.events.dlq} sin reintentos. <strong>HU-201 todavia no publica en la DLQ</strong>:
 * la enrutan los reintentos y la DLQ de HU-602. Hasta entonces el consumidor registra el
 * fallo y confirma el offset, para que un unico mensaje defectuoso no deje bloqueada la
 * particion de un pedido.
 */
public class UnsupportedEventException extends RuntimeException {

    public UnsupportedEventException(String mensaje) {
        super(mensaje);
    }

    public UnsupportedEventException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
