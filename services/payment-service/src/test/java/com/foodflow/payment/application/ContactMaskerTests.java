package com.foodflow.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El contacto es dato personal y nunca se registra completo (convenciones de implementacion). */
class ContactMaskerTests {

    @Test
    @DisplayName("deja visible solo la primera letra y el dominio")
    void enmascaraElCorreo() {
        assertThat(ContactMasker.mask("cliente@foodflow.test")).isEqualTo("c***@foodflow.test");
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
