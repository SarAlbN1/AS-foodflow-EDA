package com.foodflow.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;

import com.foodflow.payment.domain.NotificationChannel;
import com.foodflow.payment.domain.NotificationContact;
import com.foodflow.payment.domain.Payment;
import com.foodflow.payment.domain.PaymentStatus;
import com.foodflow.payment.domain.PaymentToken;
import com.foodflow.payment.domain.ProcessedEvent;
import com.foodflow.payment.domain.RejectionReason;
import com.foodflow.payment.infrastructure.messaging.PaymentEventPublisher;
import com.foodflow.payment.infrastructure.persistence.PaymentRepository;
import com.foodflow.payment.infrastructure.persistence.ProcessedEventRepository;

/**
 * HU-202 — resolucion y persistencia del pago, y HU-601 — idempotencia por {@code eventId}, con
 * los repositorios simulados. La persistencia real contra Payment DB la cubre
 * {@code PaymentServiceApplicationTests}, y la reentrega contra la base real
 * {@code PaymentIdempotencyIntegrationTests}.
 */
class PaymentApplicationServiceTests {

    private static final UUID ORDER_ID = UUID.fromString("3f8b1c2e-5a47-4d9b-8e10-7c2a6b4f9d31");

    private PaymentRepository repositorio;
    private ProcessedEventRepository procesados;
    private PaymentEventPublisher publicador;
    private PaymentApplicationService servicio;

    @BeforeEach
    void prepararServicio() {
        repositorio = mock(PaymentRepository.class);
        when(repositorio.findByOrderId(any())).thenReturn(Optional.empty());
        when(repositorio.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        procesados = mock(ProcessedEventRepository.class);
        when(procesados.existsById(any())).thenReturn(false);
        when(procesados.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        publicador = mock(PaymentEventPublisher.class);
        servicio = new PaymentApplicationService(repositorio, procesados, new TransactionReferences(), publicador);
    }

    @Test
    @DisplayName("CA-1 y CA-2: crea el pago del pedido con el monto del evento")
    void creaElPagoConElMontoRecibido() {
        Payment pago = servicio.iniciarPago(orden("45900.00", PaymentToken.PAY_OK)).orElseThrow();

        assertThat(pago.orderId()).isEqualTo(ORDER_ID);
        assertThat(pago.amount()).isEqualByComparingTo("45900.00");
        verify(repositorio).saveAndFlush(any());
    }

    @Test
    @DisplayName("CA-3: PAY-OK produce APROBADO y no lleva motivo de rechazo (ADR-10)")
    void payOkProduceAprobado() {
        Payment pago = servicio.iniciarPago(orden("45900.00", PaymentToken.PAY_OK)).orElseThrow();

        assertThat(pago.status()).isEqualTo(PaymentStatus.APROBADO);
        assertThat(pago.aprobado()).isTrue();
        assertThat(pago.reasonCode()).isNull();
    }

    @Test
    @DisplayName("CA-3: PAY-FAIL produce RECHAZADO con el motivo del catalogo (ADR-10)")
    void payFailProduceRechazado() {
        Payment pago = servicio.iniciarPago(orden("45900.00", PaymentToken.PAY_FAIL)).orElseThrow();

        assertThat(pago.status()).isEqualTo(PaymentStatus.RECHAZADO);
        assertThat(pago.aprobado()).isFalse();
        assertThat(pago.reasonCode()).isEqualTo(RejectionReason.PAGO_RECHAZADO_POR_TOKEN);
    }

    @Test
    @DisplayName("CA-4: la referencia de transaccion no es nula, tambien cuando el pago se rechaza")
    void siempreDejaUnaReferenciaDeTransaccion() {
        assertThat(servicio.iniciarPago(orden("45900.00", PaymentToken.PAY_OK)).orElseThrow().transactionReference())
                .isNotBlank();
        assertThat(servicio.iniciarPago(orden("45900.00", PaymentToken.PAY_FAIL)).orElseThrow().transactionReference())
                .isNotBlank();
    }

    @Test
    @DisplayName("CA-5: si el pedido ya tiene pago, se devuelve ese y no se crea otro")
    void noCobraDosVecesElMismoPedido() {
        Payment existente = Payment.resolver(ORDER_ID, new BigDecimal("45900.00"),
                PaymentToken.PAY_OK, "TXN-20260927-" + ORDER_ID);
        when(repositorio.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existente));

        Payment pago = servicio.iniciarPago(orden("45900.00", PaymentToken.PAY_OK)).orElseThrow();

        assertThat(pago).isSameAs(existente);
        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-203: al resolver el pago se publica su resultado")
    void publicaElResultadoDelPago() {
        StartPaymentCommand orden = orden("45900.00", PaymentToken.PAY_OK);

        Payment pago = servicio.iniciarPago(orden).orElseThrow();

        verify(publicador).publicarResultado(pago, orden);
    }

    @Test
    @DisplayName("HU-204: un pago rechazado tambien publica su resultado")
    void publicaTambienElRechazo() {
        StartPaymentCommand orden = orden("45900.00", PaymentToken.PAY_FAIL);

        Payment pago = servicio.iniciarPago(orden).orElseThrow();

        assertThat(pago.aprobado()).isFalse();
        verify(publicador).publicarResultado(pago, orden);
    }

    @Test
    @DisplayName("HU-203: reprocesar el mismo pedido no vuelve a publicar el resultado")
    void noRepiteLaPublicacionAlReprocesar() {
        Payment existente = Payment.resolver(ORDER_ID, new BigDecimal("45900.00"),
                PaymentToken.PAY_OK, "TXN-20260927-" + ORDER_ID);
        when(repositorio.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existente));

        servicio.iniciarPago(orden("45900.00", PaymentToken.PAY_OK));

        // Repetirlo haria que Order y Notification procesaran el mismo resultado dos veces.
        verify(publicador, never()).publicarResultado(any(), any());
    }

    @Test
    @DisplayName("HU-601 CA-1 y CA-2: el eventId se registra con el pago, en la misma transaccion")
    void registraElEventoProcesado() {
        StartPaymentCommand orden = orden("45900.00", PaymentToken.PAY_OK);

        servicio.iniciarPago(orden);

        ArgumentCaptor<ProcessedEvent> registro = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(procesados).saveAndFlush(registro.capture());
        assertThat(registro.getValue().eventId()).isEqualTo(orden.eventId());
        assertThat(registro.getValue().consumer()).isEqualTo(PaymentApplicationService.CONSUMIDOR);
    }

    @Test
    @DisplayName("HU-601 CA-4 y CA-5: la reentrega del mismo eventId no cobra ni publica de nuevo")
    void ignoraLaReentregaDelMismoEvento() {
        StartPaymentCommand orden = orden("45900.00", PaymentToken.PAY_OK);
        when(procesados.existsById(orden.eventId())).thenReturn(true);

        assertThat(servicio.iniciarPago(orden)).as("reentrega del mismo eventId").isEmpty();

        verify(repositorio, never()).saveAndFlush(any());
        verify(procesados, never()).saveAndFlush(any());
        verify(publicador, never()).publicarResultado(any(), any());
    }

    @Test
    @DisplayName("HU-601: un OrderCreated republicado con otro eventId tampoco cobra, pero se registra")
    void registraElEventoAunqueElPedidoYaEstuvieraCobrado() {
        Payment existente = Payment.resolver(ORDER_ID, new BigDecimal("45900.00"),
                PaymentToken.PAY_OK, "TXN-20260927-" + ORDER_ID);
        when(repositorio.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existente));
        StartPaymentCommand orden = orden("45900.00", PaymentToken.PAY_OK);

        assertThat(servicio.iniciarPago(orden)).contains(existente);

        ArgumentCaptor<ProcessedEvent> registro = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(procesados).saveAndFlush(registro.capture());
        assertThat(registro.getValue().eventId()).isEqualTo(orden.eventId());
        verify(repositorio, never()).saveAndFlush(any());
        verify(publicador, never()).publicarResultado(any(), any());
    }

    private StartPaymentCommand orden(String total, PaymentToken token) {
        return new StartPaymentCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ORDER_ID,
                new BigDecimal(total),
                "COP",
                token,
                new NotificationContact(NotificationChannel.EMAIL, "cliente@foodflow.test"));
    }
}
