package com.foodflow.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Regla de validacion del {@code X-Correlation-Id} recibido (criterio 1 de HU-403). */
class CorrelationIdFilterTests {

    private static final String UUID_REGEX =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Test
    @DisplayName("un UUID recibido se conserva, sin espacios alrededor")
    void conservaUuid() {
        assertThat(CorrelationIdFilter.resolver("11111111-2222-4333-8444-555555555555"))
                .isEqualTo("11111111-2222-4333-8444-555555555555");
        assertThat(CorrelationIdFilter.resolver("  AAAAAAAA-2222-4333-8444-555555555555 "))
                .isEqualTo("AAAAAAAA-2222-4333-8444-555555555555");
    }

    @Test
    @DisplayName("ausente, vacio o con otro formato se reemplaza por un UUID nuevo")
    void generaEnLosDemasCasos() {
        assertThat(CorrelationIdFilter.resolver(null)).matches(UUID_REGEX);
        assertThat(CorrelationIdFilter.resolver("")).matches(UUID_REGEX);
        assertThat(CorrelationIdFilter.resolver("abc")).matches(UUID_REGEX);
        assertThat(CorrelationIdFilter.resolver("11111111-2222-4333-8444-555555555555x")).matches(UUID_REGEX);
        assertThat(CorrelationIdFilter.resolver(null)).isNotEqualTo(CorrelationIdFilter.resolver(null));
    }
}
