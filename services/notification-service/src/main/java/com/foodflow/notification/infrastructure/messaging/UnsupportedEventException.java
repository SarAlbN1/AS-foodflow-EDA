package com.foodflow.notification.infrastructure.messaging;

/**
 * Un evento de {@code payments.events} no se puede procesar y reintentarlo no lo arreglaria:
 * JSON ilegible, envelope incompleto, version de contrato no soportada o payload que no cumple
 * su esquema.
 *
 * <p>Es el fallo "tecnico no recuperable" de
 * {@code docs/wiki/02-arquitectura/comportamiento-del-flujo.md}, cuyo destino es
 * {@code payments.events.dlq} sin reintentos. <strong>HU-301 todavia no publica en la DLQ</strong>:
 * la enrutan los reintentos y la DLQ de HU-602. Hasta entonces el consumidor registra el fallo y
 * confirma el offset, para que un unico mensaje defectuoso no deje bloqueada la particion de un
 * pedido.
 *
 * <p>No confundir con un fallo del proveedor: ese es un fallo de negocio, deja la notificacion
 * en {@code FALLIDA} y <strong>no</strong> va a DLQ (regla 10, HU-304).
 */
public class UnsupportedEventException extends RuntimeException {

    public UnsupportedEventException(String mensaje) {
        super(mensaje);
    }

    public UnsupportedEventException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
