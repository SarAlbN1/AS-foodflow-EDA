package com.foodflow.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lector JSON de los eventos de Kafka.
 *
 * <p>Es un {@code ObjectMapper} propio y no el de Spring Boot porque los contratos de
 * {@code contracts/events/v1/} declaran {@code additionalProperties: false}: un campo
 * desconocido es una incompatibilidad de contrato, no un dato a descartar. Jackson 3 trae
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} desactivado por defecto, asi que aqui se activa.
 */
@Configuration
public class EventJsonConfig {

    /** Nombre del bean, para inyectarlo sin ambiguedad frente al mapper de Spring Boot. */
    public static final String EVENT_OBJECT_MAPPER = "eventObjectMapper";

    @Bean(EVENT_OBJECT_MAPPER)
    public ObjectMapper eventObjectMapper() {
        return JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}
