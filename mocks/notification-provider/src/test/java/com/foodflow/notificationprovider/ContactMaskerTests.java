package com.foodflow.notificationprovider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContactMaskerTests {

    @Test
    void enmascaraLaParteLocalDelEmail() {
        assertThat(ContactMasker.mask("ana@dominio.com")).isEqualTo("a***@dominio.com");
    }

    @Test
    void noExponeUnValorSinArroba() {
        assertThat(ContactMasker.mask("sin-arroba")).isEqualTo("***");
    }
}
