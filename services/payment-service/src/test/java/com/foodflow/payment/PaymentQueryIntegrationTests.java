package com.foodflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.foodflow.payment.application.PaymentNotFoundException;
import com.foodflow.payment.application.PaymentQueryService;
import com.foodflow.payment.domain.Payment;
import com.foodflow.payment.domain.PaymentStatus;
import com.foodflow.payment.domain.PaymentToken;
import com.foodflow.payment.infrastructure.persistence.PaymentRepository;

/**
 * Criterios 2, 3 y 4 de HU-205 contra Payment DB real: la consulta se resuelve solo desde esa
 * base, devuelve el pago del pedido y no el de otro, y responde «no hay pago» sin inventarlo.
 *
 * <p>Como las demas pruebas contra base real: se omite si {@code PAYMENT_DB_URL} no esta definida
 * y deja el consumidor de Kafka parado.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@EnabledIfEnvironmentVariable(named = "PAYMENT_DB_URL", matches = ".+")
class PaymentQueryIntegrationTests {

    @Autowired
    private PaymentQueryService consultas;

    @Autowired
    private PaymentRepository pagos;

    @Test
    @DisplayName("CA2 y CA4: devuelve el pago del pedido desde Payment DB y no el de otro pedido")
    void devuelveElPagoDelPedido() {
        UUID pedido = UUID.randomUUID();
        Payment delPedido = pagos.saveAndFlush(Payment.resolver(pedido, new BigDecimal("45900.00"),
                PaymentToken.PAY_FAIL, "TXN-HU205-IT-1"));
        Payment deOtro = pagos.saveAndFlush(Payment.resolver(UUID.randomUUID(), new BigDecimal("1.00"),
                PaymentToken.PAY_OK, "TXN-HU205-IT-2"));
        try {
            Payment encontrado = consultas.pagoDelPedido(pedido);

            assertThat(encontrado.id()).isEqualTo(delPedido.id());
            assertThat(encontrado.status()).isEqualTo(PaymentStatus.RECHAZADO);
            assertThat(encontrado.amount()).isEqualByComparingTo("45900.00");
            assertThat(encontrado.transactionReference()).isEqualTo("TXN-HU205-IT-1");
            assertThat(encontrado.reasonCode()).isNotBlank();
        } finally {
            pagos.deleteAllById(java.util.List.of(delPedido.id(), deOtro.id()));
        }
    }

    @Test
    @DisplayName("CA3: sin pago registrado lanza PaymentNotFoundException en vez de inventar un resultado")
    void sinPago() {
        UUID pedido = UUID.randomUUID();

        assertThatThrownBy(() -> consultas.pagoDelPedido(pedido))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(pedido.toString());
    }
}
