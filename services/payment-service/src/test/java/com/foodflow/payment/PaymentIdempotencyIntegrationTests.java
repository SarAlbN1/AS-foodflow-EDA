package com.foodflow.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.foodflow.payment.application.PaymentApplicationService;
import com.foodflow.payment.application.StartPaymentCommand;
import com.foodflow.payment.domain.NotificationChannel;
import com.foodflow.payment.domain.NotificationContact;
import com.foodflow.payment.domain.Payment;
import com.foodflow.payment.domain.PaymentToken;
import com.foodflow.payment.infrastructure.persistence.PaymentRepository;
import com.foodflow.payment.infrastructure.persistence.ProcessedEventRepository;

/**
 * Criterios 1, 2, 4 y 5 de HU-601 contra Payment DB real: el {@code eventId} del
 * {@code OrderCreated} queda registrado en {@code processed_events} junto al pago, y entregar el
 * mismo evento dos veces no produce un segundo cobro ni un segundo evento de resultado.
 *
 * <p>Como {@code PaymentServiceApplicationTests}: se omite si {@code PAYMENT_DB_URL} no esta
 * definida. El mapeo de {@code ProcessedEvent} se comprueba de paso, porque el contexto solo
 * arranca si {@code ddl-auto=validate} lo acepta.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@EnabledIfEnvironmentVariable(named = "PAYMENT_DB_URL", matches = ".+")
class PaymentIdempotencyIntegrationTests {

    @Autowired
    private PaymentApplicationService servicio;

    @Autowired
    private PaymentRepository pagos;

    @Autowired
    private ProcessedEventRepository procesados;

    @Test
    @DisplayName("CA2, CA4 y CA5: el mismo OrderCreated entregado dos veces cobra una sola vez")
    void cobraUnaSolaVezAunqueElEventoLlegueDosVeces() {
        StartPaymentCommand orden = orden(UUID.randomUUID(), PaymentToken.PAY_OK);

        Payment pago = servicio.iniciarPago(orden).orElseThrow();
        try {
            assertThat(servicio.iniciarPago(orden)).as("reentrega del mismo eventId").isEmpty();

            assertThat(procesados.findById(orden.eventId())).isPresent();
            assertThat(pagos.findAll().stream().filter(p -> p.orderId().equals(orden.orderId())))
                    .as("un pedido, un pago")
                    .hasSize(1);
            assertThat(pagos.findById(pago.id()).orElseThrow().transactionReference())
                    .isEqualTo(pago.transactionReference());
        } finally {
            procesados.deleteById(orden.eventId());
            pagos.deleteById(pago.id());
        }
    }

    @Test
    @DisplayName("CA5: un pago rechazado tampoco se repite en la reentrega")
    void tampocoRepiteElPagoRechazado() {
        StartPaymentCommand orden = orden(UUID.randomUUID(), PaymentToken.PAY_FAIL);

        Payment pago = servicio.iniciarPago(orden).orElseThrow();
        try {
            assertThat(servicio.iniciarPago(orden)).as("reentrega del mismo eventId").isEmpty();

            assertThat(pagos.findAll().stream().filter(p -> p.orderId().equals(orden.orderId())))
                    .hasSize(1);
            assertThat(pagos.findById(pago.id()).orElseThrow().reasonCode())
                    .isEqualTo(pago.reasonCode());
        } finally {
            procesados.deleteById(orden.eventId());
            pagos.deleteById(pago.id());
        }
    }

    private StartPaymentCommand orden(UUID orderId, PaymentToken token) {
        return new StartPaymentCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                orderId,
                new BigDecimal("45900.00"),
                "COP",
                token,
                new NotificationContact(NotificationChannel.EMAIL, "cliente@foodflow.test"));
    }
}
