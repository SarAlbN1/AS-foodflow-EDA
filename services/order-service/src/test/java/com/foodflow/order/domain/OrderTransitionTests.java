package com.foodflow.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Maquina de estados del pedido ({@code comportamiento-del-flujo.md}): solo CREADO cambia, a PAGADO o a PAGO_RECHAZADO. */
class OrderTransitionTests {

    @Test
    @DisplayName("CREADO pasa a PAGADO y actualiza updatedAt")
    void creadoAPagado() throws InterruptedException {
        Order pedido = Order.crear("PED-1", NotificationChannel.EMAIL, "ana@foodflow.test", PaymentToken.PAY_OK,
                new BigDecimal("10.00"));
        var antes = pedido.updatedAt();
        Thread.sleep(2);

        assertThat(pedido.marcarPagado()).isTrue();
        assertThat(pedido.status()).isEqualTo(OrderStatus.PAGADO);
        assertThat(pedido.updatedAt()).isAfter(antes);
        assertThat(pedido.createdAt()).isBeforeOrEqualTo(antes);
    }

    @Test
    @DisplayName("un pedido que ya no esta en CREADO no cambia")
    void desdeOtroEstadoNoCambia() {
        Order pedido = Order.crear("PED-2", NotificationChannel.EMAIL, "ana@foodflow.test", PaymentToken.PAY_OK,
                new BigDecimal("10.00"));
        pedido.marcarPagado();
        var actualizado = pedido.updatedAt();

        assertThat(pedido.marcarPagado()).isFalse();
        assertThat(pedido.status()).isEqualTo(OrderStatus.PAGADO);
        assertThat(pedido.updatedAt()).isEqualTo(actualizado);
    }

    @Test
    @DisplayName("CREADO pasa a PAGO_RECHAZADO; desde PAGO_RECHAZADO ni se paga ni se vuelve a rechazar")
    void creadoAPagoRechazado() {
        Order pedido = Order.crear("PED-3", NotificationChannel.EMAIL, "ana@foodflow.test", PaymentToken.PAY_FAIL,
                new BigDecimal("10.00"));

        assertThat(pedido.marcarPagoRechazado()).isTrue();
        assertThat(pedido.status()).isEqualTo(OrderStatus.PAGO_RECHAZADO);
        assertThat(pedido.marcarPagado()).isFalse();
        assertThat(pedido.marcarPagoRechazado()).isFalse();
        assertThat(pedido.status()).isEqualTo(OrderStatus.PAGO_RECHAZADO);
    }
}
