package com.foodflow.notificationprovider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Proveedor de notificaciones simulado. Solo para desarrollo, demo y pruebas
 * (contrato: docs/wiki/03-contratos/proveedor-notificaciones.md).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NotificationProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationProviderApplication.class, args);
    }
}
