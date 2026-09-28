package com.foodflow.notificationprovider;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros de los modos de fallo.
 *
 * @param slowDelay     espera de los destinos {@code *@slow.test}; debe superar el
 *                      timeout de lectura del cliente (3 s)
 * @param flakyFailures intentos que fallan por destino {@code *@flaky.test} antes del éxito
 */
@ConfigurationProperties(prefix = "mock")
public record MockProperties(Duration slowDelay, int flakyFailures) {
}
