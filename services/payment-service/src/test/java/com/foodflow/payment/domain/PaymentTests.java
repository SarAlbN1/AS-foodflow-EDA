package com.foodflow.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HU-202 — el pago es determinista (ADR-10) y siempre queda rastreable. */
class PaymentTests {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String REFERENCIA = "TXN-20260927-3f8b1c2e";

    @Test
    @DisplayName("el importe se guarda con escala 2, como NUMERIC(12,2)")
    void ajustaLaEscalaDelImporte() {
        Payment pago = Payment.resolver(ORDER_ID, new BigDecimal("45900.0"), PaymentToken.PAY_OK, REFERENCIA);

        assertThat(pago.amount().scale()).isEqualTo(2);
        assertThat(pago.amount()).isEqualByComparingTo("45900.00");
    }

    @Test
    @DisplayName("un pago aprobado no lleva motivo de rechazo")
    void aprobadoSinMotivo() {
        Payment pago = Payment.resolver(ORDER_ID, new BigDecimal("45900.00"), PaymentToken.PAY_OK, REFERENCIA);

        assertThat(pago.status()).isEqualTo(PaymentStatus.APROBADO);
        assertThat(pago.reasonCode()).isNull();
    }

    @Test
    @DisplayName("un pago rechazado lleva el unico motivo del catalogo del prototipo")
    void rechazadoConMotivo() {
        Payment pago = Payment.resolver(ORDER_ID, new BigDecimal("45900.00"), PaymentToken.PAY_FAIL, REFERENCIA);

        assertThat(pago.status()).isEqualTo(PaymentStatus.RECHAZADO);
        assertThat(pago.reasonCode()).isEqualTo(RejectionReason.PAGO_RECHAZADO_POR_TOKEN);
    }

    @Test
    @DisplayName("no se puede resolver un pago sin referencia de transaccion")
    void exigeLaReferencia() {
        assertThatThrownBy(() -> Payment.resolver(ORDER_ID, new BigDecimal("1.00"), PaymentToken.PAY_OK, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el identificador del pago es propio y distinto del pedido")
    void generaSuPropioIdentificador() {
        Payment pago = Payment.resolver(ORDER_ID, new BigDecimal("1.00"), PaymentToken.PAY_OK, REFERENCIA);

        assertThat(pago.id()).isNotNull().isNotEqualTo(ORDER_ID);
        assertThat(pago.createdAt()).isNotNull();
        assertThat(pago.updatedAt()).isNotNull();
    }
}
