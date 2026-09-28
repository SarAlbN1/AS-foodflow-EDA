package com.foodflow.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.foodflow.order.validation.OrderDraft;
import com.foodflow.order.validation.OrderValidator;

/** HU-107 — la huella distingue un reintento de un conflicto. */
class RequestHashTests {

    private final OrderValidator validador = new OrderValidator();

    @Test
    @DisplayName("la misma solicitud produce la misma huella")
    void mismaSolicitudMismaHuella() {
        assertThat(hash("PED-1", "ana@foodflow.test", "45000.00"))
                .isEqualTo(hash("PED-1", "ana@foodflow.test", "45000.00"));
    }

    @Test
    @DisplayName("el total se normaliza: 45000 y 45000.00 son el mismo pedido")
    void laEscalaDelTotalNoCambiaLaHuella() {
        assertThat(hash("PED-1", "ana@foodflow.test", "45000"))
                .isEqualTo(hash("PED-1", "ana@foodflow.test", "45000.00"));
    }

    @Test
    @DisplayName("cambiar cualquier dato cambia la huella")
    void otroDatoOtraHuella() {
        String base = hash("PED-1", "ana@foodflow.test", "45000.00");

        assertThat(hash("PED-2", "ana@foodflow.test", "45000.00")).isNotEqualTo(base);
        assertThat(hash("PED-1", "bruno@foodflow.test", "45000.00")).isNotEqualTo(base);
        assertThat(hash("PED-1", "ana@foodflow.test", "45000.01")).isNotEqualTo(base);
    }

    @Test
    @DisplayName("la huella cabe en la columna: SHA-256 en hexadecimal son 64 caracteres")
    void cabeEnLaColumna() {
        assertThat(hash("PED-1", "ana@foodflow.test", "45000.00")).hasSize(64).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("dos campos contiguos no se pueden confundir entre si")
    void losCamposNoSeSolapan() {
        // Sin separador, "PED" + "1ana@..." y "PED1" + "ana@..." darian la misma huella.
        assertThat(hash("PED", "1ana@foodflow.test", "1.00"))
                .isNotEqualTo(hash("PED1", "ana@foodflow.test", "1.00"));
    }

    private String hash(String referencia, String contacto, String total) {
        return RequestHash.de(validador.validar(
                new OrderDraft(referencia, contacto, "EMAIL", new BigDecimal(total), "PAY-OK")));
    }
}
