package com.foodflow.payment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.kafka.support.Acknowledgment;

import com.foodflow.payment.application.PaymentApplicationService;
import com.foodflow.payment.config.EventJsonConfig;
import com.foodflow.payment.application.StartPaymentCommand;
import com.foodflow.payment.domain.NotificationChannel;
import com.foodflow.payment.domain.PaymentToken;

import tools.jackson.databind.ObjectMapper;

/**
 * HU-201 — consumo de {@code OrderCreated} desde {@code orders.events}.
 *
 * <p>Las pruebas invocan al consumidor directamente con un {@code ConsumerRecord}: verifican
 * el contrato y el filtrado sin depender de un broker. El recorrido con Kafka real lo cubre
 * HU-605.
 */
class OrderCreatedEventConsumerTests {

    private static final String TOPICO = "orders.events";

    /**
     * Ejemplo canonico del contrato. Se lee del archivo en vez de copiarlo para que un cambio
     * en {@code contracts/events/v1/} rompa esta prueba en lugar de pasar inadvertido.
     */
    private static final Path EJEMPLO_ORDER_CREATED =
            Path.of("../../contracts/events/v1/examples/validos/order-created.json");

    private ObjectMapper jackson;
    private PaymentApplicationService pagos;
    private Acknowledgment confirmacion;
    private OrderCreatedEventConsumer consumidor;

    @BeforeEach
    void prepararConsumidor() {
        jackson = new EventJsonConfig().eventObjectMapper();
        pagos = mock(PaymentApplicationService.class);
        confirmacion = mock(Acknowledgment.class);
        consumidor = new OrderCreatedEventConsumer(jackson, pagos);
    }

    @Test
    @DisplayName("CA-3: extrae orderId, monto, correlationId y el contacto del ejemplo del contrato")
    void procesaElEjemploCanonicoDelContrato() throws Exception {
        String evento = Files.readString(EJEMPLO_ORDER_CREATED);

        consumidor.consumir(registro(evento), confirmacion);

        StartPaymentCommand orden = ordenCapturada();
        assertThat(orden.orderId()).isEqualTo(UUID.fromString("3f8b1c2e-5a47-4d9b-8e10-7c2a6b4f9d31"));
        assertThat(orden.correlationId()).isEqualTo(UUID.fromString("1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9"));
        assertThat(orden.eventId()).isEqualTo(UUID.fromString("00000001-1111-4222-8333-444455556666"));
        assertThat(orden.amount()).isEqualByComparingTo(new BigDecimal("45900.00"));
        assertThat(orden.currency()).isEqualTo("COP");
        assertThat(orden.paymentToken()).isEqualTo(PaymentToken.PAY_OK);
        assertThat(orden.contact().channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(orden.contact().destination()).isEqualTo("cliente@foodflow.test");
        verify(confirmacion, times(1)).acknowledge();
    }

    @Test
    @DisplayName("HU-603: expone el contexto del evento durante el caso de uso y lo limpia despues")
    void propagaElContextoEstructurado() throws Exception {
        String evento = Files.readString(EJEMPLO_ORDER_CREATED);
        doAnswer(invocacion -> {
            assertThat(MDC.get("correlationId")).isEqualTo("1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9");
            assertThat(MDC.get("eventId")).isEqualTo("00000001-1111-4222-8333-444455556666");
            assertThat(MDC.get("eventType")).isEqualTo("OrderCreated");
            assertThat(MDC.get("orderId")).isEqualTo("3f8b1c2e-5a47-4d9b-8e10-7c2a6b4f9d31");
            return null;
        }).when(pagos).iniciarPago(any());

        consumidor.consumir(registro(evento), confirmacion);

        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("eventId")).isNull();
        assertThat(MDC.get("eventType")).isNull();
        assertThat(MDC.get("orderId")).isNull();
    }

    @Test
    @DisplayName("CA-3: PAY-FAIL tambien produce una orden valida; el resultado lo decide HU-202")
    void reconoceElTokenDeFallo() {
        consumidor.consumir(registro(eventoValido("PAY-FAIL")), confirmacion);

        assertThat(ordenCapturada().paymentToken()).isEqualTo(PaymentToken.PAY_FAIL);
    }

    @Test
    @DisplayName("CA-2: ignora OrderStatusChanged sin error y confirma el offset")
    void ignoraLosDemasTiposDelTopico() {
        String otroEvento = """
                {
                  "eventId": "00000002-1111-4222-8333-444455556666",
                  "eventType": "OrderStatusChanged",
                  "eventVersion": 1,
                  "occurredAt": "2026-09-27T20:00:00Z",
                  "correlationId": "1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9",
                  "aggregateId": "3f8b1c2e-5a47-4d9b-8e10-7c2a6b4f9d31",
                  "payload": {
                    "orderId": "3f8b1c2e-5a47-4d9b-8e10-7c2a6b4f9d31",
                    "previousStatus": "CREADO",
                    "newStatus": "PAGADO",
                    "notificationContact": { "channel": "EMAIL", "destination": "cliente@foodflow.test" }
                  }
                }
                """;

        consumidor.consumir(registro(otroEvento), confirmacion);

        verify(pagos, never()).iniciarPago(any());
        verify(confirmacion, times(1)).acknowledge();
    }

    @Test
    @DisplayName("CA-2: una version de contrato distinta de la soportada no se procesa")
    void rechazaUnaVersionNoSoportada() {
        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(eventoValido("PAY-OK").replace("\"eventVersion\": 1", "\"eventVersion\": 2")),
                confirmacion));

        verify(pagos, never()).iniciarPago(any());
        verify(confirmacion, never()).acknowledge();
    }

    @Test
    @DisplayName("CA-4: un cuerpo ilegible no inicia pago y se entrega al manejador")
    void toleraUnCuerpoIlegible() {
        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro("{ esto no es json"), confirmacion));
        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(""), confirmacion));

        verify(pagos, never()).iniciarPago(any());
        verify(confirmacion, never()).acknowledge();
    }

    @Test
    @DisplayName("CA-4: un paymentToken fuera de ADR-10 no inicia ningun pago")
    void rechazaUnTokenFueraDelContrato() {
        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(eventoValido("PAY-MAYBE")), confirmacion));

        verify(pagos, never()).iniciarPago(any());
    }

    @Test
    @DisplayName("CA-4: falta un campo obligatorio del envelope")
    void rechazaUnEnvelopeIncompleto() {
        String sinCorrelation = eventoValido("PAY-OK")
                .replace("\"correlationId\": \"1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9\",", "");

        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(sinCorrelation), confirmacion));

        verify(pagos, never()).iniciarPago(any());
    }

    @Test
    @DisplayName("CA-4: aggregateId debe ser el orderId, porque es la clave de particion (ADR-04)")
    void rechazaUnAggregateIdQueNoEsElOrderId() {
        String desalineado = eventoValido("PAY-OK")
                .replace("\"aggregateId\": \"3f8b1c2e-5a47-4d9b-8e10-7c2a6b4f9d31\"",
                        "\"aggregateId\": \"9999b1c2-5a47-4d9b-8e10-7c2a6b4f9d31\"");

        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(desalineado), confirmacion));

        verify(pagos, never()).iniciarPago(any());
    }

    @Test
    @DisplayName("CA-4: un campo desconocido en el payload es una incompatibilidad de contrato")
    void rechazaUnCampoDesconocidoEnElPayload() {
        String conExtra = eventoValido("PAY-OK")
                .replace("\"currency\": \"COP\",", "\"currency\": \"COP\", \"descuento\": 10,");

        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(conExtra), confirmacion));

        verify(pagos, never()).iniciarPago(any());
    }

    @Test
    @DisplayName("CA-4: un total con mas de dos decimales no se procesa")
    void rechazaUnTotalConDemasiadosDecimales() {
        // NUMERIC(12,2) no puede representarlo y el contrato lo declara multiplo de 0.01.
        // Sin esta comprobacion, el evento reventaria al ajustar la escala y se reintentaria
        // en vano: nunca va a poder procesarse.
        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(eventoValido("PAY-OK").replace("\"total\": 45900.00", "\"total\": 45900.005")),
                confirmacion));

        verify(pagos, never()).iniciarPago(any());
        verify(confirmacion, never()).acknowledge();
    }

    @Test
    @DisplayName("un total con ceros a la derecha si se procesa: 45900.0 son dos decimales validos")
    void aceptaCerosALaDerecha() {
        consumidor.consumir(registro(eventoValido("PAY-OK").replace("\"total\": 45900.00", "\"total\": 45900.0")),
                confirmacion);

        assertThat(ordenCapturada().amount()).isEqualByComparingTo("45900.00");
    }

    @Test
    @DisplayName("CA-4: una moneda distinta de COP no se procesa")
    void rechazaUnaMonedaFueraDelContrato() {
        assertThatExceptionOfType(UnsupportedEventException.class)
                .isThrownBy(() -> consumidor.consumir(registro(eventoValido("PAY-OK").replace("\"COP\"", "\"USD\"")), confirmacion));

        verify(pagos, never()).iniciarPago(any());
    }

    private StartPaymentCommand ordenCapturada() {
        ArgumentCaptor<StartPaymentCommand> captor = ArgumentCaptor.forClass(StartPaymentCommand.class);
        verify(pagos).iniciarPago(captor.capture());
        return captor.getValue();
    }

    private ConsumerRecord<String, String> registro(String valor) {
        // La clave del mensaje es el orderId: es la clave de particion (regla 11).
        return new ConsumerRecord<>(TOPICO, 0, 0L, "3f8b1c2e-5a47-4d9b-8e10-7c2a6b4f9d31", valor);
    }

    private String eventoValido(String paymentToken) {
        return """
                {
                  "eventId": "00000001-1111-4222-8333-444455556666",
                  "eventType": "OrderCreated",
                  "eventVersion": 1,
                  "occurredAt": "2026-09-27T20:00:00Z",
                  "correlationId": "1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9",
                  "aggregateId": "3f8b1c2e-5a47-4d9b-8e10-7c2a6b4f9d31",
                  "payload": {
                    "orderId": "3f8b1c2e-5a47-4d9b-8e10-7c2a6b4f9d31",
                    "customerReference": "CLI-000123",
                    "total": 45900.00,
                    "currency": "COP",
                    "paymentToken": "%s",
                    "notificationContact": { "channel": "EMAIL", "destination": "cliente@foodflow.test" }
                  }
                }
                """.formatted(paymentToken);
    }
}
