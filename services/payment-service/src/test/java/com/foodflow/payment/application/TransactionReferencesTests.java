package com.foodflow.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HU-202, criterio 4: la referencia de transaccion es identificable y no nula. */
class TransactionReferencesTests {

    private static final UUID ORDER_ID = UUID.fromString("3f8b1c2e-5a47-4d9b-8e10-7c2a6b4f9d31");

    @Test
    @DisplayName("lleva la fecha UTC y el prefijo del pedido, y cabe en el contrato")
    void componeUnaReferenciaRastreable() {
        String referencia = new TransactionReferences().nueva(ORDER_ID, Instant.parse("2026-09-27T20:00:00Z"));

        assertThat(referencia).isEqualTo("TXN-20260927-3f8b1c2e");
        // El contrato de payment-approved limita transactionReference a 100 caracteres.
        assertThat(referencia).hasSizeLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("la fecha se toma en UTC, no en la zona local")
    void usaUtc() {
        String referencia = new TransactionReferences().nueva(ORDER_ID, Instant.parse("2026-09-28T02:00:00Z"));

        assertThat(referencia).isEqualTo("TXN-20260928-3f8b1c2e");
    }
}
