package com.foodflow.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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
import com.foodflow.order.validation.ValidatedOrderCommand;

/**
 * Casos de uso del pedido con las escrituras simuladas: criterios 1, 2, 4 y 5 de HU-101 y
 * criterios 2, 3 y 4 de HU-102. Las decisiones de idempotencia van en {@code IdempotencyTests}.
 */
class OrderApplicationServiceTests {

    private static final UUID CORRELACION = UUID.fromString("1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9");
    private static final String CLAVE = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

    private OrderRepository repositorio;
    private OrderCreationTransaction transaccion;
    private OrderApplicationService servicio;

    @BeforeEach
    void prepararServicio() {
        repositorio = mock(OrderRepository.class);
        transaccion = mock(OrderCreationTransaction.class);
        when(transaccion.buscarClave(anyString())).thenReturn(Optional.empty());
        // El colaborador transaccional construye el pedido con el comando ya validado.
        when(transaccion.crear(any(), anyString(), anyString(), any())).thenAnswer(invocacion -> {
            ValidatedOrderCommand c = invocacion.getArgument(0);
            return Order.crear(c.customerReference(), c.notificationChannel(), c.customerContact(),
                    c.paymentToken(), c.total());
        });
        servicio = new OrderApplicationService(new OrderValidator(), repositorio, transaccion);
    }

    @Test
    @DisplayName("el pedido se crea en CREADO, con identificador propio y el snapshot de ADR-11")
    void creaPedidoEnCreado() {
        Order pedido = servicio.crearPedido(borrador("45000.00"), CORRELACION, CLAVE);

        assertThat(pedido.id()).isNotNull();
        assertThat(pedido.status()).isEqualTo(OrderStatus.CREADO);
        assertThat(pedido.total()).isEqualByComparingTo("45000.00");
        assertThat(pedido.customerContact()).isEqualTo("ana@foodflow.test");
        assertThat(pedido.notificationChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(pedido.paymentToken()).isEqualTo(PaymentToken.PAY_OK);
    }

    @Test
    @DisplayName("dos pedidos reciben identificadores distintos")
    void identificadoresUnicos() {
        UUID uno = servicio.crearPedido(borrador("1.00"), CORRELACION, CLAVE).id();
        UUID otro = servicio.crearPedido(borrador("1.00"), CORRELACION, "otra-clave").id();

        assertThat(uno).isNotEqualTo(otro);
    }

    @Test
    @DisplayName("una entrada invalida no escribe nada en Order DB")
    void entradaInvalidaNoPersiste() {
        OrderDraft invalido = new OrderDraft("PED-2", "sin-arroba", "SMS", BigDecimal.ZERO, "PAY-QUIZAS");

        assertThatExceptionOfType(OrderValidationException.class)
                .isThrownBy(() -> servicio.crearPedido(invalido, CORRELACION, CLAVE));

        verify(transaccion, never()).crear(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("la consulta devuelve el pedido persistido y lo lee de Order DB en cada llamada")
    void consultaLeeLaBaseEnCadaLlamada() {
        Order pedido = Order.crear("PED-3", NotificationChannel.EMAIL, "ana@foodflow.test",
                PaymentToken.PAY_FAIL, new BigDecimal("12500.50"));
        when(repositorio.findById(pedido.id())).thenReturn(Optional.of(pedido));

        assertThat(servicio.consultarPedido(pedido.id())).isSameAs(pedido);
        assertThat(servicio.consultarPedido(pedido.id())).isSameAs(pedido);

        verify(repositorio, org.mockito.Mockito.times(2)).findById(pedido.id());
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

    private static OrderDraft borrador(String total) {
        return new OrderDraft("PED-0001", "ana@foodflow.test", "EMAIL", new BigDecimal(total), "PAY-OK");
    }
}
