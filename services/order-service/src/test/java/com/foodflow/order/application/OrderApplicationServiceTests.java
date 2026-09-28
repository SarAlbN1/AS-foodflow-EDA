package com.foodflow.order.application;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.foodflow.order.domain.NotificationChannel;
import com.foodflow.order.domain.Order;
import com.foodflow.order.domain.OrderStatus;
import com.foodflow.order.domain.PaymentToken;
import com.foodflow.order.infrastructure.persistence.OrderRepository;
import com.foodflow.order.validation.OrderDraft;
import com.foodflow.order.validation.OrderValidationException;
import com.foodflow.order.validation.OrderValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Casos de uso del pedido con el repositorio simulado: criterios 1, 2, 4 y 5 de HU-101 y
 * criterios 2, 3 y 4 de HU-102.
 */
class OrderApplicationServiceTests {

    private final OrderRepository repositorio = mock(OrderRepository.class);
    private final OrderApplicationService servicio = new OrderApplicationService(new OrderValidator(), repositorio);

    @Test
    @DisplayName("el pedido se crea en CREADO, con identificador propio y el snapshot de ADR-11")
    void creaPedidoEnCreado() {
        when(repositorio.save(any(Order.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Order pedido = servicio.crearPedido(new OrderDraft(
                "PED-0001", "ana@foodflow.test", "EMAIL", new BigDecimal("45000.00"), "PAY-OK"));

        assertThat(pedido.id()).isNotNull();
        assertThat(pedido.status()).isEqualTo(OrderStatus.CREADO);
        assertThat(pedido.total()).isEqualByComparingTo("45000.00");
        assertThat(pedido.customerContact()).isEqualTo("ana@foodflow.test");
        assertThat(pedido.notificationChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(pedido.paymentToken()).isEqualTo(PaymentToken.PAY_OK);
        assertThat(pedido.createdAt()).isNotNull();
        assertThat(pedido.updatedAt()).isEqualTo(pedido.createdAt());
        verify(repositorio).save(pedido);
    }

    @Test
    @DisplayName("dos pedidos reciben identificadores distintos")
    void identificadoresUnicos() {
        when(repositorio.save(any(Order.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        OrderDraft draft = new OrderDraft("PED-1", "ana@foodflow.test", "EMAIL", new BigDecimal("1.00"), "PAY-OK");

        assertThat(servicio.crearPedido(draft).id()).isNotEqualTo(servicio.crearPedido(draft).id());
    }

    @Test
    @DisplayName("una entrada invalida no escribe nada en Order DB")
    void entradaInvalidaNoPersiste() {
        OrderDraft invalido = new OrderDraft("PED-2", "sin-arroba", "SMS", BigDecimal.ZERO, "PAY-QUIZAS");

        assertThatExceptionOfType(OrderValidationException.class)
                .isThrownBy(() -> servicio.crearPedido(invalido));

        verify(repositorio, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("la consulta devuelve el pedido persistido y lo lee de Order DB en cada llamada")
    void consultaLeeLaBaseEnCadaLlamada() {
        Order pedido = Order.crear("PED-3", NotificationChannel.EMAIL, "ana@foodflow.test",
                PaymentToken.PAY_FAIL, new BigDecimal("12500.50"));
        when(repositorio.findById(pedido.id())).thenReturn(Optional.of(pedido));

        assertThat(servicio.consultarPedido(pedido.id())).isSameAs(pedido);
        assertThat(servicio.consultarPedido(pedido.id())).isSameAs(pedido);

        // Criterios 2 y 4 de HU-102: sin cache y sin otra fuente que Order DB.
        verify(repositorio, times(2)).findById(pedido.id());
        verifyNoMoreInteractions(repositorio);
    }

    @Test
    @DisplayName("un identificador inexistente produce OrderNotFoundException")
    void pedidoInexistente() {
        UUID id = UUID.randomUUID();
        when(repositorio.findById(id)).thenReturn(Optional.empty());

        assertThatExceptionOfType(OrderNotFoundException.class)
                .isThrownBy(() -> servicio.consultarPedido(id))
                .withMessage("no existe un pedido con id " + id);
    }
}
