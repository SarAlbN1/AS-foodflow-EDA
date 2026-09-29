package com.foodflow.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El contacto es dato personal y nunca se registra completo (convenciones de implementacion).
 * Es la misma regla con la que el contrato REST devuelve {@code destination}.
 */
class ContactMaskerTests {

    @Test
    @DisplayName("deja visible solo la primera letra y el dominio")
    void enmascaraElCorreo() {
        assertThat(ContactMasker.mask("cliente@foodflow.test")).isEqualTo("c***@foodflow.test");
    }

    @Test
    @DisplayName("el resultado cumple el patron que exige el contrato REST")
    void cumpleElPatronDelContrato() {
        assertThat(ContactMasker.mask("ana@foodflow.test")).matches("^[^@][*]{3}@[^@]+$");
    }

    @Test
    @DisplayName("un valor sin arroba no deja ver nada")
    void enmascaraUnValorSinArroba() {
        assertThat(ContactMasker.mask("sin-arroba")).isEqualTo("***");
        assertThat(ContactMasker.mask("@solo-dominio")).isEqualTo("***");
    }

    @Test
    @DisplayName("tolera ausencia de contacto")
    void toleraValoresVacios() {
        assertThat(ContactMasker.mask(null)).isEqualTo("(sin contacto)");
        assertThat(ContactMasker.mask("  ")).isEqualTo("(sin contacto)");
    }
}
