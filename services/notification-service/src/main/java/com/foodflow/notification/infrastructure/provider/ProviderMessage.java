package com.foodflow.notification.infrastructure.provider;

/**
 * Cuerpo de {@code POST /v1/messages} del proveedor
 * ({@code docs/wiki/03-contratos/proveedor-notificaciones.md}).
 *
 * <p>Los cuatro campos son los que fija el contrato. El destino y el contenido salen de la
 * notificacion; el {@code correlationId} viene del evento de pago y permite cruzar el envio con
 * los registros de los tres servicios.
 */
record ProviderMessage(String channel, String destination, String content, String correlationId) {
}
