package com.foodflow.order.infrastructure.messaging;

/**
 * Evento que no se puede procesar tal como llego: cuerpo ilegible, envelope incompleto, version
 * no soportada o payload fuera de contrato. Es un error no recuperable: repetirlo no lo arregla
 * ({@code comportamiento-del-flujo.md}). Hoy se registra y se confirma el offset; HU-602 lo
 * llevara a {@code payments.events.dlq}.
 */
class UnsupportedEventException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UnsupportedEventException(String mensaje) {
        super(mensaje);
    }

    UnsupportedEventException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
