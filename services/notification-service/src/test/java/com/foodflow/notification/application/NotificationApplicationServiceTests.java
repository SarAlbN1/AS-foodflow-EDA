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
import com.foodflow.notification.domain.NotificationStatus;
import com.foodflow.notification.domain.ProcessedEvent;
import com.foodflow.notification.infrastructure.messaging.NotificationEventPublisher;
import com.foodflow.notification.infrastructure.persistence.NotificationRepository;
import com.foodflow.notification.infrastructure.persistence.ProcessedEventRepository;

/**
 * HU-301 — creacion de la notificacion, con las escrituras simuladas. La persistencia real
 * contra Notification DB la cubre {@code NotificationServiceApplicationTests}.
 */
class NotificationApplicationServiceTests {

    private static final UUID ORDER_ID = UUID.fromString("3f8b1c2e-5a47-4d9b-8e10-7c2a6b4f9d31");
    private static final UUID PAYMENT_ID = UUID.fromString("b71e4d6a-2c58-4f13-9a0e-5d8c3b1f7e42");
    private static final UUID EVENT_ID = UUID.fromString("00000003-1111-4222-8333-444455556666");

    private NotificationRepository notificaciones;
    private static final UUID CORRELACION = UUID.fromString("1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9");

    private ProcessedEventRepository procesados;
    private NotificationEventPublisher publicador;
    private NotificationApplicationService servicio;

    @BeforeEach
    void prepararServicio() {
        notificaciones = mock(NotificationRepository.class);
        procesados = mock(ProcessedEventRepository.class);
        when(procesados.existsById(any())).thenReturn(false);
        when(notificaciones.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        publicador = mock(NotificationEventPublisher.class);
        servicio = new NotificationApplicationService(notificaciones, procesados, publicador);
    }

    @Test
    @DisplayName("CA-2 y CA-3: crea la notificacion en PENDIENTE con los datos del evento")
    void creaLaNotificacionPendiente() {
        Notification creada = servicio.notificarResultado(orden(true)).orElseThrow();

        assertThat(creada.status()).isEqualTo(NotificationStatus.PENDIENTE);
        assertThat(creada.orderId()).isEqualTo(ORDER_ID);
        assertThat(creada.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(creada.channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(creada.destination()).isEqualTo("cliente@foodflow.test");
        assertThat(creada.content()).isNotBlank();
        // Todavia no se ha intentado nada: el envio es HU-302.
        assertThat(creada.attempts()).isZero();
        assertThat(creada.failureCode()).isNull();
    }

    @Test
    @DisplayName("CA-3: el contenido depende del resultado del pago")
    void elContenidoDependeDelResultado() {
        assertThat(servicio.notificarResultado(orden(true)).orElseThrow().content())
                .contains("aprobado");
        assertThat(servicio.notificarResultado(orden(false)).orElseThrow().content())
                .contains("rechazado");
    }

    @Test
    @DisplayName("CA-4: el mismo eventId no genera una segunda notificacion")
    void elMismoEventoNoDuplica() {
        when(procesados.existsById(EVENT_ID)).thenReturn(true);

        assertThat(servicio.notificarResultado(orden(true))).isEmpty();

        verify(notificaciones, never()).saveAndFlush(any());
        verify(procesados, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("CA-4: el eventId queda registrado junto con la notificacion (ADR-09)")
    void registraElEventoProcesado() {
        servicio.notificarResultado(orden(true));

        org.mockito.ArgumentCaptor<ProcessedEvent> captor =
                org.mockito.ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(procesados).saveAndFlush(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(EVENT_ID);
        assertThat(captor.getValue().consumer()).isEqualTo(NotificationApplicationService.CONSUMIDOR);
    }

    @Test
    @DisplayName("CA-5: no se consulta ninguna otra base; todo sale del evento")
    void noConsultaOtraBase() {
        Notification creada = servicio.notificarResultado(orden(true)).orElseThrow();

        // Destino y canal vienen del snapshot del evento (ADR-11), no de una consulta.
        assertThat(creada.destination()).isEqualTo("cliente@foodflow.test");
        assertThat(creada.channel()).isEqualTo(NotificationChannel.EMAIL);
        // Los unicos colaboradores son los dos repositorios de su propia base.
        verify(notificaciones).saveAndFlush(any());
        verify(procesados).existsById(EVENT_ID);
        verify(procesados).saveAndFlush(any());
        org.mockito.Mockito.verifyNoMoreInteractions(notificaciones, procesados);
    }

    private NotifyPaymentResultCommand orden(boolean aprobado) {
        return new NotifyPaymentResultCommand(EVENT_ID, UUID.randomUUID(), ORDER_ID, PAYMENT_ID,
                new BigDecimal("45900.00"), "COP", aprobado, NotificationChannel.EMAIL,
                "cliente@foodflow.test");
    }

    @Test
    @DisplayName("criterios 1, 2 y 3 de HU-303: registra el envio y publica NotificationSent")
    void registraElEnvioYPublica() {
        Notification pendiente = Notification.pendiente(UUID.randomUUID(), UUID.randomUUID(),
                NotificationChannel.EMAIL, "ana@foodflow.test", "Tu pago de 1,00 COP fue aprobado.");
        when(notificaciones.findById(pendiente.id())).thenReturn(Optional.of(pendiente));

        boolean registrado = servicio.registrarEnvio(pendiente.id(), "MOCK-1", 2, CORRELACION);

        assertThat(registrado).isTrue();
        assertThat(pendiente.status()).isEqualTo(NotificationStatus.ENVIADA);
        assertThat(pendiente.attempts()).isEqualTo(2);
        verify(notificaciones).saveAndFlush(pendiente);
        verify(publicador).publicarEnviada(pendiente, "MOCK-1", CORRELACION);
    }

    @Test
    @DisplayName("criterio 5: una notificacion que ya no esta PENDIENTE no se toca ni publica de nuevo")
    void noPublicaDosVecesElMismoEnvio() {
        Notification yaEnviada = Notification.pendiente(UUID.randomUUID(), UUID.randomUUID(),
                NotificationChannel.EMAIL, "ana@foodflow.test", "Tu pago de 1,00 COP fue aprobado.");
        yaEnviada.marcarEnviada(1);
        when(notificaciones.findById(yaEnviada.id())).thenReturn(Optional.of(yaEnviada));

        assertThat(servicio.registrarEnvio(yaEnviada.id(), "MOCK-2", 3, CORRELACION)).isFalse();

        assertThat(yaEnviada.attempts()).isEqualTo(1);
        verify(notificaciones, never()).saveAndFlush(yaEnviada);
        verify(publicador, never()).publicarEnviada(any(), anyString(), any());
    }

    @Test
    @DisplayName("si la notificacion no existe no se publica nada")
    void sinNotificacionNoPublica() {
        UUID desconocida = UUID.randomUUID();
        when(notificaciones.findById(desconocida)).thenReturn(Optional.empty());

        assertThat(servicio.registrarEnvio(desconocida, "MOCK-3", 1, CORRELACION)).isFalse();

        verify(publicador, never()).publicarEnviada(any(), anyString(), any());
    }

    @Test
    @DisplayName("criterios 1 y 2 de HU-304: deja la notificacion FALLIDA y publica NotificationFailed")
    void registraElFalloYPublica() {
        Notification pendiente = Notification.pendiente(UUID.randomUUID(), UUID.randomUUID(),
                NotificationChannel.EMAIL, "ana@foodflow.test", "Tu pago de 1,00 COP fue aprobado.");
        when(notificaciones.findById(pendiente.id())).thenReturn(Optional.of(pendiente));

        boolean registrado = servicio.registrarFallo(pendiente.id(), "PROVEEDOR_NO_DISPONIBLE", 3,
                CORRELACION);

        assertThat(registrado).isTrue();
        assertThat(pendiente.status()).isEqualTo(NotificationStatus.FALLIDA);
        assertThat(pendiente.failureCode()).isEqualTo("PROVEEDOR_NO_DISPONIBLE");
        assertThat(pendiente.attempts()).isEqualTo(3);
        verify(notificaciones).saveAndFlush(pendiente);
        verify(publicador).publicarFallida(pendiente, CORRELACION);
    }

    @Test
    @DisplayName("criterio 3: registrar un fallo no lanza, para que el offset se confirme y nada se revierta")
    void elFalloNoSePropaga() {
        Notification yaEnviada = Notification.pendiente(UUID.randomUUID(), UUID.randomUUID(),
                NotificationChannel.EMAIL, "ana@foodflow.test", "Tu pago de 1,00 COP fue aprobado.");
        yaEnviada.marcarEnviada(1);
        when(notificaciones.findById(yaEnviada.id())).thenReturn(Optional.of(yaEnviada));

        // Ni siquiera cuando la transicion es imposible: si lanzara, el consumidor no confirmaria
        // el offset, Kafka reentregaria y el pago quedaria sin registrar en Order (reglas 10 y 12).
        assertThat(servicio.registrarFallo(yaEnviada.id(), "PROVEEDOR_NO_DISPONIBLE", 3, CORRELACION))
                .isFalse();

        assertThat(yaEnviada.status()).isEqualTo(NotificationStatus.ENVIADA);
        verify(publicador, never()).publicarFallida(any(), any());
    }
}
