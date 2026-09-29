package com.foodflow.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.foodflow.notification.domain.Notification;
import com.foodflow.notification.domain.NotificationChannel;

/**
 * Orquestacion de HU-302: registrar y entregar. Lo que se comprueba aqui son las decisiones, no
 * la entrega; el envio real contra un servidor HTTP lo cubre
 * {@code ProviderNotificationSenderTests} y contra el proveedor simulado,
 * {@code ProviderDeliveryIntegrationTest}.
 */
class NotificationDispatcherTests {

    private NotificationApplicationService notificaciones;
    private NotificationSender proveedor;
    private NotificationDispatcher despachador;

    @BeforeEach
    void prepararDespachador() {
        notificaciones = mock(NotificationApplicationService.class);
        proveedor = mock(NotificationSender.class);
        when(proveedor.enviar(any(), anyString())).thenReturn(DeliveryOutcome.aceptado("MOCK-1", 1));
        despachador = new NotificationDispatcher(notificaciones, proveedor);
    }

    @Test
    @DisplayName("criterio 1: la notificacion registrada se entrega al proveedor")
    void entregaLaNotificacionRegistrada() {
        Notification notificacion = pendiente();
        when(notificaciones.notificarResultado(any())).thenReturn(Optional.of(notificacion));

        Optional<DeliveryOutcome> resultado = despachador.procesar(orden());

        assertThat(resultado).isPresent();
        assertThat(resultado.get().aceptado()).isTrue();
        verify(proveedor).enviar(notificacion, CORRELACION.toString());
    }

    @Test
    @DisplayName("un evento ya procesado no produce un segundo envio al proveedor")
    void laReentregaNoVuelveAEnviar() {
        // ADR-09: notificarResultado devuelve vacio cuando el eventId ya estaba registrado.
        when(notificaciones.notificarResultado(any())).thenReturn(Optional.empty());

        assertThat(despachador.procesar(orden())).isEmpty();

        verify(proveedor, never()).enviar(any(), anyString());
    }

    @Test
    @DisplayName("criterio 4: un fallo del proveedor no se propaga como excepcion")
    void elFalloDelProveedorNoSePropaga() {
        when(notificaciones.notificarResultado(any())).thenReturn(Optional.of(pendiente()));
        when(proveedor.enviar(any(), anyString()))
                .thenReturn(DeliveryOutcome.fallido(DeliveryFailure.PROVEEDOR_NO_DISPONIBLE, 3));

        // Si esto lanzara, el consumidor no confirmaria el offset y bloquearia la particion; y el
        // fallo de una notificacion no debe afectar al registro del pago (reglas 10 y 12).
        Optional<DeliveryOutcome> resultado = despachador.procesar(orden());

        assertThat(resultado).isPresent();
        assertThat(resultado.get().aceptado()).isFalse();
        assertThat(resultado.get().failure()).isEqualTo(DeliveryFailure.PROVEEDOR_NO_DISPONIBLE);
        assertThat(resultado.get().attempts()).isEqualTo(3);
    }

    private static final UUID CORRELACION = UUID.fromString("8cce3b97-fc2f-4e1a-98d8-631c9c18d5b9");

    private static NotifyPaymentResultCommand orden() {
        return new NotifyPaymentResultCommand(UUID.randomUUID(), CORRELACION, UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("45900.00"), "COP", true,
                NotificationChannel.EMAIL, "ana@foodflow.test");
    }

    private static Notification pendiente() {
        return Notification.pendiente(UUID.randomUUID(), UUID.randomUUID(), NotificationChannel.EMAIL,
                "ana@foodflow.test", "Tu pago de 45.900,00 COP fue aprobado.");
    }
}
