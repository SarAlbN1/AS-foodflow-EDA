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
    @DisplayName("lleva la fecha UTC y el pedido completo, y cabe en el contrato")
    void componeUnaReferenciaRastreable() {
        String referencia = new TransactionReferences().nueva(ORDER_ID, Instant.parse("2026-09-27T20:00:00Z"));

        assertThat(referencia).isEqualTo("TXN-20260927-3f8b1c2e-5a47-4d9b-8e10-7c2a6b4f9d31");
        // El contrato de payment-approved limita transactionReference a 100 caracteres.
        assertThat(referencia).hasSizeLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("dos pedidos distintos del mismo dia no comparten referencia, aunque coincida su prefijo")
    void noColisionaEntrePedidosConElMismoPrefijo() {
        TransactionReferences referencias = new TransactionReferences();
        Instant mismoDia = Instant.parse("2026-09-27T20:00:00Z");
        // Mismos 8 primeros digitos, pedidos distintos: con un prefijo compartirian referencia.
        UUID otro = UUID.fromString("3f8b1c2e-0000-4000-8000-000000000000");

        assertThat(referencias.nueva(ORDER_ID, mismoDia))
                .isNotEqualTo(referencias.nueva(otro, mismoDia));
    }

    @Test
    @DisplayName("la fecha se toma en UTC, no en la zona local")
    void usaUtc() {
        String referencia = new TransactionReferences().nueva(ORDER_ID, Instant.parse("2026-09-28T02:00:00Z"));

        assertThat(referencia).startsWith("TXN-20260928-");
    }
}
