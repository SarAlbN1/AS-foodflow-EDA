package com.foodflow.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
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
import com.foodflow.payment.domain.PaymentStatus;
import com.foodflow.payment.domain.PaymentToken;
import com.foodflow.payment.domain.RejectionReason;
import com.foodflow.payment.infrastructure.persistence.PaymentRepository;

/**
 * Arranque del contexto completo y persistencia real en Payment DB (criterios 1, 2, 4 y 5 de
 * HU-202). Comprueba ademas que el mapeo JPA coincide con
 * {@code infrastructure/postgres/payment-db/01-schema.sql}, porque el contexto solo arranca si
 * {@code ddl-auto=validate} lo acepta.
 *
 * <p>Necesita la base levantada ({@code docker compose -f infrastructure/compose/docker-compose.yml
 * up -d payment-db}) y las variables de {@code .env}. Se omite cuando {@code PAYMENT_DB_URL} no
 * esta definida, para que {@code ./mvnw verify} siga funcionando sin infraestructura; el
 * prototipo no usa Testcontainers (es opcional, HU-011).
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@EnabledIfEnvironmentVariable(named = "PAYMENT_DB_URL", matches = ".+")
class PaymentServiceApplicationTests {

    @Autowired
    private PaymentApplicationService servicio;

    @Autowired
    private PaymentRepository repositorio;

    @Test
    void contextLoads() {
        assertThat(servicio).isNotNull();
    }

    @Test
    @DisplayName("PAY-OK persiste un pago APROBADO con su referencia de transaccion")
    void persisteUnPagoAprobado() {
        UUID orderId = UUID.randomUUID();

        Payment pago = servicio.iniciarPago(orden(orderId, "45900.00", PaymentToken.PAY_OK));

        try {
            Optional<Payment> guardado = repositorio.findByOrderId(orderId);

            assertThat(guardado).isPresent();
            assertThat(guardado.get().status()).isEqualTo(PaymentStatus.APROBADO);
            assertThat(guardado.get().amount()).isEqualByComparingTo("45900.00");
            assertThat(guardado.get().transactionReference()).isNotBlank();
            assertThat(guardado.get().reasonCode()).isNull();
        } finally {
            repositorio.deleteById(pago.id());
        }
    }

    @Test
    @DisplayName("PAY-FAIL persiste un pago RECHAZADO con su motivo")
    void persisteUnPagoRechazado() {
        UUID orderId = UUID.randomUUID();

        Payment pago = servicio.iniciarPago(orden(orderId, "45900.00", PaymentToken.PAY_FAIL));

        try {
            Payment guardado = repositorio.findByOrderId(orderId).orElseThrow();

            assertThat(guardado.status()).isEqualTo(PaymentStatus.RECHAZADO);
            assertThat(guardado.reasonCode()).isEqualTo(RejectionReason.PAGO_RECHAZADO_POR_TOKEN);
            assertThat(guardado.transactionReference()).isNotBlank();
        } finally {
            repositorio.deleteById(pago.id());
        }
    }

    @Test
    @DisplayName("criterio 5: reprocesar el mismo pedido no crea un segundo pago")
    void noDuplicaElPagoDeUnPedido() {
        UUID orderId = UUID.randomUUID();

        Payment primero = servicio.iniciarPago(orden(orderId, "45900.00", PaymentToken.PAY_OK));
        Payment segundo = servicio.iniciarPago(orden(orderId, "45900.00", PaymentToken.PAY_OK));

        try {
            assertThat(segundo.id()).isEqualTo(primero.id());
            assertThat(repositorio.findAll().stream().filter(p -> p.orderId().equals(orderId)))
                    .hasSize(1);
        } finally {
            repositorio.deleteById(primero.id());
        }
    }

    private StartPaymentCommand orden(UUID orderId, String total, PaymentToken token) {
        return new StartPaymentCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                orderId,
                new BigDecimal(total),
                "COP",
                token,
                new NotificationContact(NotificationChannel.EMAIL, "cliente@foodflow.test"));
    }
}
