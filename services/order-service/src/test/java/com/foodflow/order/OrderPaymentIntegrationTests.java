package com.foodflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.foodflow.order.application.OrderApplicationService;
import com.foodflow.order.application.OrderPaymentService;
import com.foodflow.order.application.PaymentResultCommand;
import com.foodflow.order.domain.NotificationChannel;
import com.foodflow.order.domain.Order;
import com.foodflow.order.domain.OrderStatus;
import com.foodflow.order.domain.PaymentToken;
import com.foodflow.order.infrastructure.persistence.OrderRepository;
import com.foodflow.order.infrastructure.persistence.ProcessedEventRepository;

/**
 * Criterios 2, 3 y 4 de HU-104 contra Order DB real: el cambio de estado queda persistido, el
 * {@code eventId} se registra en {@code processed_events} y la reentrega no produce otro efecto.
 *
 * <p>Como {@code OrderServiceApplicationTests}: se omite si {@code ORDER_DB_URL} no esta definida.
 * El mapeo de {@code ProcessedEvent} se comprueba de paso, porque el contexto solo arranca si
 * {@code ddl-auto=validate} lo acepta.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ORDER_DB_URL", matches = ".+")
class OrderPaymentIntegrationTests {

    @Autowired
    private OrderPaymentService pagos;

    @Autowired
    private OrderRepository pedidos;

    @Autowired
    private OrderApplicationService consultas;

    @Autowired
    private ProcessedEventRepository procesados;

    @Test
    @DisplayName("CA2, CA3 y CA4: PAGADO persistido una sola vez aunque el evento llegue dos veces")
    void pagaUnaSolaVez() {
        Order pedido = pedidos.saveAndFlush(Order.crear("PED-HU104", NotificationChannel.EMAIL,
                "ana@foodflow.test", PaymentToken.PAY_OK, new BigDecimal("45000.00")));
        PaymentResultCommand resultado =
                new PaymentResultCommand(UUID.randomUUID(), UUID.randomUUID(), pedido.id(), UUID.randomUUID());

        try {
            assertThat(pagos.registrarPagoAprobado(resultado)).isPresent();
            assertThat(pagos.registrarPagoAprobado(resultado)).as("reentrega del mismo eventId").isEmpty();

            Order guardado = pedidos.findById(pedido.id()).orElseThrow();
            assertThat(guardado.status()).isEqualTo(OrderStatus.PAGADO);
            assertThat(guardado.updatedAt()).isAfter(guardado.createdAt());
            assertThat(procesados.findById(resultado.eventId())).isPresent();
        } finally {
            procesados.deleteById(resultado.eventId());
            pedidos.deleteById(pedido.id());
        }
    }

    @Test
    @DisplayName("otro evento de aprobacion sobre un pedido ya PAGADO se ignora sin error")
    void segundaAprobacionIgnorada() {
        Order pedido = pedidos.saveAndFlush(Order.crear("PED-HU104-B", NotificationChannel.EMAIL,
                "ana@foodflow.test", PaymentToken.PAY_OK, new BigDecimal("1.00")));
        PaymentResultCommand primero =
                new PaymentResultCommand(UUID.randomUUID(), UUID.randomUUID(), pedido.id(), UUID.randomUUID());
        PaymentResultCommand segundo =
                new PaymentResultCommand(UUID.randomUUID(), UUID.randomUUID(), pedido.id(), UUID.randomUUID());

        try {
            pagos.registrarPagoAprobado(primero);
            assertThat(pagos.registrarPagoAprobado(segundo).orElseThrow().status()).isEqualTo(OrderStatus.PAGADO);
            assertThat(procesados.findById(segundo.eventId())).isPresent();
        } finally {
            procesados.deleteById(primero.eventId());
            procesados.deleteById(segundo.eventId());
            pedidos.deleteById(pedido.id());
        }
    }

    @Test
    @DisplayName("HU-105 CA2 a CA5: PAGO_RECHAZADO persistido una vez y visible en la consulta del pedido")
    void rechazaUnaSolaVezYSeConsulta() {
        Order pedido = pedidos.saveAndFlush(Order.crear("PED-HU105", NotificationChannel.EMAIL,
                "ana@foodflow.test", PaymentToken.PAY_FAIL, new BigDecimal("12500.50")));
        PaymentResultCommand resultado =
                new PaymentResultCommand(UUID.randomUUID(), UUID.randomUUID(), pedido.id(), UUID.randomUUID());

        try {
            assertThat(pagos.registrarPagoRechazado(resultado)).isPresent();
            assertThat(pagos.registrarPagoRechazado(resultado)).as("reentrega del mismo eventId").isEmpty();

            // CA5: la misma consulta que atiende GET /orders/{id} ve el estado nuevo.
            assertThat(consultas.consultarPedido(pedido.id()).status()).isEqualTo(OrderStatus.PAGO_RECHAZADO);
            assertThat(procesados.findById(resultado.eventId())).isPresent();
        } finally {
            procesados.deleteById(resultado.eventId());
            pedidos.deleteById(pedido.id());
        }
    }
}
