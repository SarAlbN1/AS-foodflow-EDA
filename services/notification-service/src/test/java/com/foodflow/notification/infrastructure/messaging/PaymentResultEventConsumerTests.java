package com.foodflow.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

import com.foodflow.notification.application.NotificationDispatcher;
import com.foodflow.notification.application.NotifyPaymentResultCommand;
import com.foodflow.notification.config.EventJsonConfig;
import com.foodflow.notification.domain.NotificationChannel;

import tools.jackson.databind.ObjectMapper;

/**
 * HU-301 — consumo de {@code payments.events}.
 *
 * <p>Las pruebas invocan al consumidor con un {@code ConsumerRecord}: verifican el contrato y el
 * filtrado sin depender de un broker. El recorrido con Kafka real lo cubre HU-605.
 */
class PaymentResultEventConsumerTests {

    private static final String TOPICO = "payments.events";
    private static final String ORDER_ID = "3f8b1c2e-5a47-4d9b-8e10-7c2a6b4f9d31";

    /**
     * Ejemplos canonicos del contrato. Se leen del archivo en vez de copiarlos para que un
     * cambio en {@code contracts/events/v1/} rompa estas pruebas en lugar de pasar inadvertido.
     */
    private static final Path EJEMPLO_APROBADO =
            Path.of("../../contracts/events/v1/examples/validos/payment-approved.json");

    private static final Path EJEMPLO_RECHAZADO =
            Path.of("../../contracts/events/v1/examples/validos/payment-rejected.json");

    private ObjectMapper jackson;
    private NotificationDispatcher notificaciones;
    private Acknowledgment confirmacion;
    private PaymentResultEventConsumer consumidor;

    @BeforeEach
    void prepararConsumidor() {
        jackson = new EventJsonConfig().eventObjectMapper();
        notificaciones = mock(NotificationDispatcher.class);
        when(notificaciones.procesar(any())).thenReturn(Optional.empty());
        confirmacion = mock(Acknowledgment.class);
        consumidor = new PaymentResultEventConsumer(jackson, notificaciones);
    }

    @Test
    @DisplayName("CA-1 y CA-5: procesa el PaymentApproved del contrato y saca el contacto del evento")
    void procesaElAprobadoCanonico() throws Exception {
        consumidor.consumir(registro(Files.readString(EJEMPLO_APROBADO)), confirmacion);

        NotifyPaymentResultCommand orden = ordenCapturada();
        assertThat(orden.aprobado()).isTrue();
        assertThat(orden.orderId()).isEqualTo(UUID.fromString(ORDER_ID));
        assertThat(orden.paymentId()).isEqualTo(UUID.fromString("b71e4d6a-2c58-4f13-9a0e-5d8c3b1f7e42"));
        assertThat(orden.eventId()).isEqualTo(UUID.fromString("00000003-1111-4222-8333-444455556666"));
        assertThat(orden.correlationId()).isEqualTo(UUID.fromString("1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9"));
        assertThat(orden.amount()).isEqualByComparingTo("45900.00");
        assertThat(orden.currency()).isEqualTo("COP");
        // ADR-11: el destino y el canal salen del snapshot del evento, sin consultar nada.
        assertThat(orden.channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(orden.destination()).isEqualTo("cliente@foodflow.test");
        verify(confirmacion, times(1)).acknowledge();
    }

    @Test
    @DisplayName("CA-1: procesa tambien el PaymentRejected del contrato")
    void procesaElRechazadoCanonico() throws Exception {
        consumidor.consumir(registro(Files.readString(EJEMPLO_RECHAZADO)), confirmacion);

        assertThat(ordenCapturada().aprobado()).isFalse();
    }

    @Test
    @DisplayName("CA-1: ignora sin error cualquier otro tipo de evento del topico")
    void ignoraOtrosTipos() {
        consumidor.consumir(registro(evento("OrderStatusChanged", true)), confirmacion);

        // Ignorado por tipo, no "no procesable": no va a la DLQ y su offset se confirma.
        verify(notificaciones, never()).procesar(any());
        verify(confirmacion).acknowledge();
    }

    @Test
    @DisplayName("una version de contrato distinta de la soportada no se procesa")
    void rechazaUnaVersionNoSoportada() {
        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(evento("PaymentApproved", true)
                .replace("\"eventVersion\": 1", "\"eventVersion\": 2")), confirmacion));

        verify(notificaciones, never()).procesar(any());
        verify(confirmacion, never()).acknowledge();
    }

    @Test
    @DisplayName("un cuerpo ilegible no crea notificacion y se entrega al manejador")
    void toleraUnCuerpoIlegible() {
        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro("{ esto no es json"), confirmacion));
        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(""), confirmacion));

        verify(notificaciones, never()).procesar(any());
        verify(confirmacion, never()).acknowledge();
    }

    @Test
    @DisplayName("un PaymentApproved sin transactionReference incumple su contrato")
    void elAprobadoExigeSuCampoDistintivo() {
        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(evento("PaymentApproved", false)), confirmacion));

        verify(notificaciones, never()).procesar(any());
    }

    @Test
    @DisplayName("un PaymentRejected sin reasonCode incumple su contrato")
    void elRechazadoExigeSuCampoDistintivo() {
        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(evento("PaymentRejected", false)), confirmacion));

        verify(notificaciones, never()).procesar(any());
    }

    @Test
    @DisplayName("aggregateId debe ser el orderId, porque es la clave de particion (ADR-04)")
    void rechazaUnAggregateIdQueNoEsElOrderId() {
        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(evento("PaymentApproved", true)
                .replace("\"aggregateId\": \"" + ORDER_ID + "\"",
                        "\"aggregateId\": \"9999b1c2-5a47-4d9b-8e10-7c2a6b4f9d31\"")), confirmacion));

        verify(notificaciones, never()).procesar(any());
    }

    @Test
    @DisplayName("sin notificationContact no se puede notificar sin consultar otra base")
    void exigeElSnapshotDeContacto() {
        String sinContacto = evento("PaymentApproved", true)
                .replace(",\n    \"notificationContact\": { \"channel\": \"EMAIL\", \"destination\": \"cliente@foodflow.test\" }", "");

        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(sinContacto), confirmacion));

        verify(notificaciones, never()).procesar(any());
    }

    @Test
    @DisplayName("una moneda distinta de COP no se procesa")
    void rechazaUnaMonedaFueraDelContrato() {
        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(evento("PaymentApproved", true).replace("\"COP\"", "\"USD\"")),
                confirmacion));

        verify(notificaciones, never()).procesar(any());
    }

    @Test
    @DisplayName("un campo desconocido en el payload es una incompatibilidad de contrato")
    void rechazaUnCampoDesconocido() {
        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(evento("PaymentApproved", true)
                .replace("\"currency\": \"COP\",", "\"currency\": \"COP\", \"descuento\": 10,")), confirmacion));

        verify(notificaciones, never()).procesar(any());
    }

    private NotifyPaymentResultCommand ordenCapturada() {
        ArgumentCaptor<NotifyPaymentResultCommand> captor =
                ArgumentCaptor.forClass(NotifyPaymentResultCommand.class);
        verify(notificaciones).procesar(captor.capture());
        return captor.getValue();
    }

    private ConsumerRecord<String, String> registro(String valor) {
        // La clave del mensaje es el orderId: es la clave de particion (regla 11).
        return new ConsumerRecord<>(TOPICO, 0, 0L, ORDER_ID, valor);
    }

    /**
     * Evento con el campo distintivo de su tipo presente o ausente, para comprobar que cada
     * esquema exige el suyo.
     */
    private String evento(String eventType, boolean conCampoDistintivo) {
        String distintivo = switch (eventType) {
            case "PaymentApproved" -> conCampoDistintivo ? "\"transactionReference\": \"TXN-20260927-" + ORDER_ID + "\"," : "";
            case "PaymentRejected" -> conCampoDistintivo ? "\"reasonCode\": \"PAGO_RECHAZADO_POR_TOKEN\"," : "";
            default -> "";
        };
        return """
                {
                  "eventId": "00000003-1111-4222-8333-444455556666",
                  "eventType": "%s",
                  "eventVersion": 1,
                  "occurredAt": "2026-09-27T20:00:00Z",
                  "correlationId": "1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9",
                  "aggregateId": "%s",
                  "payload": {
                    "paymentId": "b71e4d6a-2c58-4f13-9a0e-5d8c3b1f7e42",
                    "orderId": "%s",
                    "amount": 45900.00,
                    "currency": "COP",
                    %s
                    "notificationContact": { "channel": "EMAIL", "destination": "cliente@foodflow.test" }
                  }
                }
                """.formatted(eventType, ORDER_ID, ORDER_ID, distintivo);
    }
}
