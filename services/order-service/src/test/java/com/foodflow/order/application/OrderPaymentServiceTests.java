package com.foodflow.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.foodflow.order.domain.NotificationChannel;
import com.foodflow.order.domain.Order;
import com.foodflow.order.domain.OrderStatus;
import com.foodflow.order.domain.PaymentToken;
import com.foodflow.order.domain.ProcessedEvent;
import com.foodflow.order.infrastructure.messaging.OrderEventPublisher;
import com.foodflow.order.infrastructure.persistence.OrderRepository;
import com.foodflow.order.infrastructure.persistence.ProcessedEventRepository;

/** Caso de uso de HU-104 con los repositorios simulados. La base real la cubre {@code OrderPaymentIntegrationTests}. */
class OrderPaymentServiceTests {

    private final OrderRepository pedidos = mock(OrderRepository.class);
    private final ProcessedEventRepository procesados = mock(ProcessedEventRepository.class);
    private final OrderEventPublisher publicador = mock(OrderEventPublisher.class);
    private final OrderPaymentService servicio = new OrderPaymentService(pedidos, procesados, publicador);

    @Test
    @DisplayName("CA2 y CA4: el pedido pasa de CREADO a PAGADO y el eventId se registra en la misma operacion")
    void pagaElPedido() {
        Order pedido = pedidoCreado();
        PaymentResultCommand resultado = resultado(pedido.id());
        when(pedidos.findById(pedido.id())).thenReturn(Optional.of(pedido));

        Optional<Order> aplicado = servicio.registrarPagoAprobado(resultado);

        assertThat(aplicado).contains(pedido);
        assertThat(pedido.status()).isEqualTo(OrderStatus.PAGADO);
        ArgumentCaptor<ProcessedEvent> registro = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(procesados).saveAndFlush(registro.capture());
        assertThat(registro.getValue().eventId()).isEqualTo(resultado.eventId());
        assertThat(registro.getValue().consumer()).isEqualTo("order-service.payments");
        // HU-106 CA1 y CA3: publica el cambio con el correlationId del evento de pago.
        verify(publicador).publicarOrderStatusChanged(pedido, OrderStatus.CREADO, resultado.correlationId());
    }

    @Test
    @DisplayName("CA3: un eventId ya procesado no vuelve a tocar el pedido")
    void eventoRepetido() {
        PaymentResultCommand resultado = resultado(UUID.randomUUID());
        when(procesados.existsById(resultado.eventId())).thenReturn(true);

        assertThat(servicio.registrarPagoAprobado(resultado)).isEmpty();

        verify(pedidos, never()).findById(any());
        verify(procesados, never()).saveAndFlush(any());
        verify(publicador, never()).publicarOrderStatusChanged(any(), any(), any());
    }

    @Test
    @DisplayName("una transicion invalida se ignora sin error y el evento queda registrado")
    void transicionInvalida() {
        Order pedido = pedidoCreado();
        pedido.marcarPagado();
        when(pedidos.findById(pedido.id())).thenReturn(Optional.of(pedido));

        Optional<Order> aplicado = servicio.registrarPagoAprobado(resultado(pedido.id()));

        assertThat(aplicado.orElseThrow().status()).isEqualTo(OrderStatus.PAGADO);
        verify(procesados).saveAndFlush(any());
        // HU-106 CA4: una transicion invalida no produce evento.
        verify(publicador, never()).publicarOrderStatusChanged(any(), any(), any());
    }

    @Test
    @DisplayName("un pedido inexistente es recuperable: se lanza y no se registra el evento")
    void pedidoInexistente() {
        PaymentResultCommand resultado = resultado(UUID.randomUUID());
        when(pedidos.findById(resultado.orderId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrarPagoAprobado(resultado))
                .isInstanceOf(OrderNotFoundException.class);
        verify(procesados, never()).saveAndFlush(any());
        verify(publicador, never()).publicarOrderStatusChanged(any(), any(), any());
    }

    @Test
    @DisplayName("HU-105 CA2: el pedido pasa de CREADO a PAGO_RECHAZADO y el eventId se registra")
    void rechazaElPedido() {
        Order pedido = pedidoCreado();
        PaymentResultCommand resultado = resultado(pedido.id());
        when(pedidos.findById(pedido.id())).thenReturn(Optional.of(pedido));

        assertThat(servicio.registrarPagoRechazado(resultado)).contains(pedido);
        assertThat(pedido.status()).isEqualTo(OrderStatus.PAGO_RECHAZADO);
        verify(procesados).saveAndFlush(any());
        verify(publicador).publicarOrderStatusChanged(pedido, OrderStatus.CREADO, resultado.correlationId());
    }

    @Test
    @DisplayName("HU-105: un rechazo sobre un pedido ya PAGADO se ignora; el pago aprobado no se deshace")
    void rechazoTrasPagoIgnorado() {
        Order pedido = pedidoCreado();
        pedido.marcarPagado();
        when(pedidos.findById(pedido.id())).thenReturn(Optional.of(pedido));

        assertThat(servicio.registrarPagoRechazado(resultado(pedido.id())).orElseThrow().status())
                .isEqualTo(OrderStatus.PAGADO);
        verify(publicador, never()).publicarOrderStatusChanged(any(), any(), any());
    }

    @Test
    @DisplayName("HU-105 CA3: un rechazo ya procesado no vuelve a tocar el pedido")
    void rechazoRepetido() {
        PaymentResultCommand resultado = resultado(UUID.randomUUID());
        when(procesados.existsById(resultado.eventId())).thenReturn(true);

        assertThat(servicio.registrarPagoRechazado(resultado)).isEmpty();
        verify(pedidos, never()).findById(any());
    }

    private static Order pedidoCreado() {
        return Order.crear("PED-0104", NotificationChannel.EMAIL, "ana@foodflow.test", PaymentToken.PAY_OK,
                new BigDecimal("45000.00"));
    }

    private static PaymentResultCommand resultado(UUID orderId) {
        return new PaymentResultCommand(UUID.randomUUID(), UUID.randomUUID(), orderId, UUID.randomUUID());
    }
}
