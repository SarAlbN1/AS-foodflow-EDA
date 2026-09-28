package com.foodflow.order.validation;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.foodflow.order.domain.NotificationChannel;
import com.foodflow.order.domain.PaymentToken;
import com.foodflow.order.validation.OrderValidationException.Violation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/** Criterio 3 de HU-101: total, contacto, canal y token de pago. */
class OrderValidatorTests {

    private final OrderValidator validador = new OrderValidator();

    private static OrderDraft draft(String referencia, String contacto, String canal, String total, String token) {
        return new OrderDraft(referencia, contacto, canal, total == null ? null : new BigDecimal(total), token);
    }

    private static OrderDraft valido() {
        return draft("PED-0001", "ana@foodflow.test", "EMAIL", "45000.00", "PAY-OK");
    }

    @Test
    @DisplayName("un borrador valido se convierte a tipos del dominio")
    void borradorValido() {
        ValidatedOrderCommand comando = validador.validar(valido());

        assertThat(comando.customerReference()).isEqualTo("PED-0001");
        assertThat(comando.customerContact()).isEqualTo("ana@foodflow.test");
        assertThat(comando.notificationChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(comando.total()).isEqualByComparingTo("45000.00");
        assertThat(comando.paymentToken()).isEqualTo(PaymentToken.PAY_OK);
    }

    @Test
    @DisplayName("PAY-FAIL tambien es un token valido (ADR-10)")
    void tokenDeFalloEsValido() {
        assertThat(validador.validar(draft("PED-2", "ana@foodflow.test", "EMAIL", "1.00", "PAY-FAIL")).paymentToken())
                .isEqualTo(PaymentToken.PAY_FAIL);
    }

    @Test
    @DisplayName("el total debe ser mayor que cero")
    void totalNoPositivo() {
        assertThat(camposRechazados(draft("PED-3", "ana@foodflow.test", "EMAIL", "0", "PAY-OK")))
                .containsExactly("total");
        assertThat(camposRechazados(draft("PED-3", "ana@foodflow.test", "EMAIL", "-10.00", "PAY-OK")))
                .containsExactly("total");
    }

    @Test
    @DisplayName("el total admite maximo dos decimales y diez digitos enteros")
    void totalFueraDeEscala() {
        assertThat(camposRechazados(draft("PED-4", "ana@foodflow.test", "EMAIL", "10.005", "PAY-OK")))
                .containsExactly("total");
        assertThat(camposRechazados(draft("PED-4", "ana@foodflow.test", "EMAIL", "12345678901.00", "PAY-OK")))
                .containsExactly("total");
    }

    @Test
    @DisplayName("el contacto debe tener formato de correo")
    void contactoInvalido() {
        assertThat(camposRechazados(draft("PED-5", "ana-arroba-foodflow", "EMAIL", "1.00", "PAY-OK")))
                .containsExactly("customerContact");
    }

    @Test
    @DisplayName("el canal solo admite EMAIL")
    void canalInvalido() {
        assertThat(camposRechazados(draft("PED-6", "ana@foodflow.test", "SMS", "1.00", "PAY-OK")))
                .containsExactly("notificationChannel");
        assertThat(camposRechazados(draft("PED-6", "ana@foodflow.test", "email", "1.00", "PAY-OK")))
                .containsExactly("notificationChannel");
    }

    @Test
    @DisplayName("el token de pago solo admite PAY-OK o PAY-FAIL")
    void tokenInvalido() {
        assertThat(camposRechazados(draft("PED-7", "ana@foodflow.test", "EMAIL", "1.00", "PAY-QUIZAS")))
                .containsExactly("paymentToken");
    }

    @Test
    @DisplayName("los campos obligatorios vacios se rechazan todos juntos")
    void obligatoriosAusentes() {
        assertThat(camposRechazados(draft("   ", null, "", null, null)))
                .containsExactly("customerReference", "customerContact", "notificationChannel", "total",
                        "paymentToken");
    }

    @Test
    @DisplayName("la referencia admite maximo 60 caracteres")
    void referenciaDemasiadoLarga() {
        assertThat(camposRechazados(draft("P".repeat(61), "ana@foodflow.test", "EMAIL", "1.00", "PAY-OK")))
                .containsExactly("customerReference");
    }

    @Test
    @DisplayName("el detalle del error enumera campo y motivo")
    void detalleLegible() {
        assertThatExceptionOfType(OrderValidationException.class)
                .isThrownBy(() -> validador.validar(draft("PED-8", "ana@foodflow.test", "EMAIL", "0", "PAY-OK")))
                .withMessage("total: debe ser mayor que cero");
    }

    private List<String> camposRechazados(OrderDraft draft) {
        try {
            validador.validar(draft);
            throw new AssertionError("se esperaba OrderValidationException");
        } catch (OrderValidationException excepcion) {
            return excepcion.violations().stream().map(Violation::campo).toList();
        }
    }
}
