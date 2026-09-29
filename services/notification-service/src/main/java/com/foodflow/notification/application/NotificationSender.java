package com.foodflow.notification.application;

import com.foodflow.notification.domain.Notification;

/**
 * Puerto de salida hacia el proveedor externo de notificaciones.
 *
 * <p>La capa de aplicacion depende de esta interfaz y no del cliente HTTP: el adaptador vive en
 * {@code infrastructure.provider}, que es el <strong>unico</strong> paquete del sistema
 * autorizado a hacer llamadas HTTP salientes (regla arquitectonica 7, vigilada por
 * {@code ArchitectureTest}).
 */
public interface NotificationSender {

    /**
     * Intenta entregar la notificacion y devuelve el resultado.
     *
     * <p><strong>No lanza excepcion por un fallo del proveedor</strong> (criterio 4): un
     * {@code 5xx}, un {@code 4xx} o un tiempo agotado son resultados posibles del negocio, no
     * averias del servicio. Reintentar, si procede, es responsabilidad de la implementacion.
     *
     * @param correlationId correlacion del flujo, para cruzar el envio con los registros
     */
    DeliveryOutcome enviar(Notification notificacion, String correlationId);
}
