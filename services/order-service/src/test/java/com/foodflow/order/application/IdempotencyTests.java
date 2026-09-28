package com.foodflow.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.dao.DataIntegrityViolationException;

import com.foodflow.order.domain.IdempotencyKey;
import com.foodflow.order.domain.NotificationChannel;
import com.foodflow.order.domain.Order;
import com.foodflow.order.domain.PaymentToken;
import com.foodflow.order.infrastructure.persistence.OrderRepository;
import com.foodflow.order.validation.OrderDraft;
import com.foodflow.order.validation.OrderValidator;

/**
 * HU-107 — la misma solicitud no crea dos pedidos.
 *
 * <p>Las escrituras estan simuladas: lo que se verifica aqui es la decision (reintento,
 * conflicto o pedido nuevo) y que un reintento no vuelva a escribir ni a publicar. La
 * persistencia real contra Order DB la cubre {@code OrderServiceApplicationTests}.
 */
class IdempotencyTests {

    private static final String CLAVE = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    private static final UUID CORRELACION = UUID.fromString("1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9");

    private OrderCreationTransaction transaccion;
    private OrderApplicationService servicio;

    @BeforeEach
    void prepararServicio() {
        transaccion = mock(OrderCreationTransaction.class);
        when(transaccion.buscarClave(anyString())).thenReturn(Optional.empty());
        when(transaccion.crear(any(), anyString(), anyString(), any()))
                .thenAnswer(invocacion -> pedido());
        servicio = new OrderApplicationService(
                new OrderValidator(), mock(OrderRepository.class), transaccion);
    }

    @Test
    @DisplayName("CA-2: la misma clave con el mismo cuerpo devuelve el pedido original sin crear otro")
    void mismaClaveMismoCuerpo() {
        Order original = pedido();
        when(transaccion.buscarClave(CLAVE))
                .thenReturn(Optional.of(IdempotencyKey.de(CLAVE, hashDe(borrador()), original.id())));
        when(transaccion.buscarPedido(original.id())).thenReturn(Optional.of(original));

        Order devuelto = servicio.crearPedido(borrador(), CORRELACION, CLAVE);

        assertThat(devuelto.id()).isEqualTo(original.id());
        // No se crea otro pedido y, por tanto, tampoco se publica otro OrderCreated.
        verify(transaccion, never()).crear(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("CA-3: la misma clave con un cuerpo distinto produce conflicto")
    void mismaClaveOtroCuerpo() {
        when(transaccion.buscarClave(CLAVE)).thenReturn(Optional.of(
                IdempotencyKey.de(CLAVE, "otra-huella-distinta", UUID.randomUUID())));

        assertThatExceptionOfType(IdempotencyConflictException.class)
                .isThrownBy(() -> servicio.crearPedido(borrador(), CORRELACION, CLAVE))
                .withMessageContaining(CLAVE);

        verify(transaccion, never()).crear(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("una clave nueva crea el pedido y registra la clave")
    void claveNuevaCrea() {
        servicio.crearPedido(borrador(), CORRELACION, CLAVE);

        verify(transaccion).crear(any(), anyString(), eq(CLAVE), eq(CORRELACION));
    }

    @Test
    @DisplayName("CA-2: un cuerpo equivalente cuenta como el mismo intento, no como conflicto")
    void elCuerpoSeComparaPorSusDatos() {
        // Mismo pedido con el total escrito de otra forma: 45000 y 45000.00 son lo mismo.
        OrderDraft conOtraEscala = new OrderDraft(
                "PED-0001", "ana@foodflow.test", "EMAIL", new BigDecimal("45000"), "PAY-OK");
        Order original = pedido();
        when(transaccion.buscarClave(CLAVE))
                .thenReturn(Optional.of(IdempotencyKey.de(CLAVE, hashDe(borrador()), original.id())));
        when(transaccion.buscarPedido(original.id())).thenReturn(Optional.of(original));

        Order devuelto = servicio.crearPedido(conOtraEscala, CORRELACION, CLAVE);

        assertThat(devuelto.id()).isEqualTo(original.id());
    }

    @Test
    @DisplayName("dos solicitudes simultaneas con la misma clave devuelven un solo pedido")
    void carreraEntreSolicitudesSimultaneas() {
        Order delQueGano = pedido();
        // La primera consulta no ve la clave, pero al escribir otra solicitud ya la ha insertado.
        when(transaccion.crear(any(), anyString(), anyString(), any()))
                .thenThrow(new DataIntegrityViolationException("clave duplicada"));
        when(transaccion.buscarClave(CLAVE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(IdempotencyKey.de(CLAVE, hashDe(borrador()), delQueGano.id())));
        when(transaccion.buscarPedido(delQueGano.id())).thenReturn(Optional.of(delQueGano));

        Order devuelto = servicio.crearPedido(borrador(), CORRELACION, CLAVE);

        assertThat(devuelto.id()).isEqualTo(delQueGano.id());
    }

    @Test
    @DisplayName("si la escritura falla por otra cosa, el error sube")
    void otroFalloDeEscrituraNoSeDisimula() {
        when(transaccion.crear(any(), anyString(), anyString(), any()))
                .thenThrow(new DataIntegrityViolationException("restriccion ajena"));
        when(transaccion.buscarClave(CLAVE)).thenReturn(Optional.empty());

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> servicio.crearPedido(borrador(), CORRELACION, CLAVE));
    }

    private static String hashDe(OrderDraft draft) {
        return RequestHash.de(new OrderValidator().validar(draft));
    }

    private static OrderDraft borrador() {
        return new OrderDraft("PED-0001", "ana@foodflow.test", "EMAIL",
                new BigDecimal("45000.00"), "PAY-OK");
    }

    private static Order pedido() {
        return Order.crear("PED-0001", NotificationChannel.EMAIL, "ana@foodflow.test",
                PaymentToken.PAY_OK, new BigDecimal("45000.00"));
    }
}
