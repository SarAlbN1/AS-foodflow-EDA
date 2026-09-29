package com.foodflow.notification.infrastructure.provider;

/**
 * Respuesta {@code 202} del proveedor: la referencia con la que el proveedor identifica el envio.
 *
 * <p>El prototipo no la persiste —la tabla {@code notifications} no tiene esa columna
 * ({@code docs/wiki/03-contratos/persistencia.md})—, pero se registra en el log, que es lo que
 * permite cruzar un envio con los registros del proveedor simulado.
 */
record ProviderAcceptance(String providerReference) {
}
