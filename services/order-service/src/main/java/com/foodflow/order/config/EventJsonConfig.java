package com.foodflow.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escritor JSON de los eventos que publica Order Service.
 *
 * <p>Es un {@code ObjectMapper} propio y no el de Spring Boot para que el formato de los
 * eventos no dependa de ajustes pensados para el API HTTP. Lo unico que se fija aqui es que
 * las fechas salgan en ISO 8601 UTC y no como marca de tiempo numerica, que es lo que exige
 * el patron de {@code occurredAt} en {@code contracts/events/v1/envelope.schema.json}.
 */
@Configuration
public class EventJsonConfig {

    /** Nombre del bean, para inyectarlo sin ambiguedad frente al mapper de Spring Boot. */
    public static final String EVENT_OBJECT_MAPPER = "eventObjectMapper";

    @Bean(EVENT_OBJECT_MAPPER)
    public ObjectMapper eventObjectMapper() {
        return JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
