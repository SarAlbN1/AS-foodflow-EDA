package com.foodflow.notification.application;

/**
 * Motivos por los que un envio al proveedor externo termina en fallo (HU-302, criterio 4).
 *
 * <p>Este es el catalogo de {@code notifications.failure_code} y del {@code failureCode} de
 * {@code NotificationFailed}: su esquema
 * ({@code contracts/events/v1/notification-failed.schema.json}) lo deja sin {@code enum} y remite
 * a esta historia para fijarlo. Los nombres caben en {@code VARCHAR(50)}.
 *
 * <p><strong>La distincion que importa</strong> es si el fallo se reintenta. Un {@code 4xx} dice
 * que el mensaje es invalido: repetirlo produce el mismo rechazo, asi que se abandona en el
 * primer intento. Los demas son transitorios por definicion y agotan la politica de reintentos
 * antes de darse por perdidos.
 */
public enum DeliveryFailure {

    /** El proveedor respondio {@code 5xx} en todos los intentos. */
    PROVEEDOR_NO_DISPONIBLE(true),

    /** Se agoto el tiempo de conexion o de lectura en todos los intentos. */
    TIEMPO_DE_ESPERA_AGOTADO(true),

    /** No se pudo establecer la conexion: el proveedor no responde en esa direccion. */
    ERROR_DE_CONEXION(true),

    /** El proveedor respondio {@code 4xx}: el mensaje no es aceptable y no se reintenta. */
    PROVEEDOR_RECHAZO_EL_MENSAJE(false),

    /**
     * El proveedor respondio algo que el contrato no contempla: un estado que no es {@code 2xx},
     * {@code 4xx} ni {@code 5xx} —una redireccion, por ejemplo—, o un fallo del cliente al
     * tratar la respuesta. No se reintenta: una respuesta fuera de contrato no cambia por
     * repetir la peticion, y casi siempre significa que la URL configurada no es la del
     * proveedor.
     */
    RESPUESTA_INESPERADA(false);

    private final boolean transitorio;

    DeliveryFailure(boolean transitorio) {
        this.transitorio = transitorio;
    }

    /** {@code true} si vale la pena volver a intentarlo. */
    public boolean esTransitorio() {
        return transitorio;
    }
}
