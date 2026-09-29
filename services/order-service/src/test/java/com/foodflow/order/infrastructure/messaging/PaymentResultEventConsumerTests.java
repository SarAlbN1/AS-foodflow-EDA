package com.foodflow.order.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

import com.foodflow.order.application.OrderNotFoundException;
import com.foodflow.order.application.OrderPaymentService;
import com.foodflow.order.application.PaymentResultCommand;
import com.foodflow.order.config.EventJsonConfig;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Consumo de {@code payments.events} en Order Service (HU-104), sin broker: el registro de Kafka
 * se construye a mano y el caso de uso esta simulado.
 *
 * <p>El evento de partida es el <strong>ejemplo valido del contrato</strong>
 * ({@code contracts/events/v1/examples/validos/payment-approved.json}), el mismo que valida
 * {@code scripts/validate-events.sh}: lo que se prueba es lo que Payment Service publica.
 */
class PaymentResultEventConsumerTests {

    private static final Path EJEMPLO =
            Path.of("../../contracts/events/v1/examples/validos/payment-approved.json");
    private static final Path EJEMPLO_RECHAZO =
            Path.of("../../contracts/events/v1/examples/validos/payment-rejected.json");

    private final ObjectMapper jackson = new EventJsonConfig().eventObjectMapper();
    private final OrderPaymentService pagos = mock(OrderPaymentService.class);
    private final Acknowledgment confirmacion = mock(Acknowledgment.class);
    private final PaymentResultEventConsumer consumidor = new PaymentResultEventConsumer(jackson, pagos);

    @Test
    @DisplayName("CA1: un PaymentApproved del contrato se aplica al pedido y se confirma el offset")
    void aplicaElPagoAprobado() throws Exception {
        ObjectNode evento = ejemplo();

        consumidor.consumir(registro(evento.toString()), confirmacion);

        ArgumentCaptor<PaymentResultCommand> orden = ArgumentCaptor.forClass(PaymentResultCommand.class);
        verify(pagos).registrarPagoAprobado(orden.capture());
        assertThat(orden.getValue().eventId()).isEqualTo(UUID.fromString(evento.get("eventId").asText()));
        assertThat(orden.getValue().orderId()).isEqualTo(UUID.fromString(evento.get("aggregateId").asText()));
        assertThat(orden.getValue().paymentId())
                .isEqualTo(UUID.fromString(evento.get("payload").get("paymentId").asText()));
        assertThat(orden.getValue().correlationId())
                .isEqualTo(UUID.fromString(evento.get("correlationId").asText()));
        verify(confirmacion).acknowledge();
    }

    @Test
    @DisplayName("HU-105 CA1: un PaymentRejected del contrato se aplica al pedido y se confirma el offset")
    void aplicaElPagoRechazado() throws Exception {
        ObjectNode evento = (ObjectNode) jackson.readTree(Files.readString(EJEMPLO_RECHAZO));

        consumidor.consumir(registro(evento.toString()), confirmacion);

        ArgumentCaptor<PaymentResultCommand> orden = ArgumentCaptor.forClass(PaymentResultCommand.class);
        verify(pagos).registrarPagoRechazado(orden.capture());
        verify(pagos, never()).registrarPagoAprobado(any());
        assertThat(orden.getValue().eventId()).isEqualTo(UUID.fromString(evento.get("eventId").asText()));
        assertThat(orden.getValue().orderId()).isEqualTo(UUID.fromString(evento.get("aggregateId").asText()));
        verify(confirmacion).acknowledge();
    }

    @Test
    @DisplayName("HU-105: un PaymentRejected sin reasonCode o con el campo del aprobado se descarta")
    void rechazoFueraDeContrato() throws Exception {
        ObjectNode sinMotivo = (ObjectNode) jackson.readTree(Files.readString(EJEMPLO_RECHAZO));
        ((ObjectNode) sinMotivo.get("payload")).remove("reasonCode");
        ObjectNode conReferencia = (ObjectNode) jackson.readTree(Files.readString(EJEMPLO_RECHAZO));
        ((ObjectNode) conReferencia.get("payload")).put("transactionReference", "TXN-X");

        consumidor.consumir(registro(sinMotivo.toString()), confirmacion);
        consumidor.consumir(registro(conReferencia.toString()), confirmacion);

        verifyNoInteractions(pagos);
        verify(confirmacion, org.mockito.Mockito.times(2)).acknowledge();
    }

    @Test
    @DisplayName("otros tipos se ignoran y se confirma el offset")
    void ignoraOtrosTipos() throws Exception {
        for (String tipo : new String[] {"OrderCreated", "Desconocido"}) {
            ObjectNode evento = ejemplo();
            evento.put("eventType", tipo);

            consumidor.consumir(registro(evento.toString()), confirmacion);
        }

        verifyNoInteractions(pagos);
        verify(confirmacion, org.mockito.Mockito.times(2)).acknowledge();
    }

    @Test
    @DisplayName("un cuerpo ilegible se descarta sin tumbar al consumidor y se confirma")
    void cuerpoIlegible() {
        consumidor.consumir(registro("{esto no es json"), confirmacion);
        consumidor.consumir(registro(""), confirmacion);

        verifyNoInteractions(pagos);
        verify(confirmacion, org.mockito.Mockito.times(2)).acknowledge();
    }

    @Test
    @DisplayName("aggregateId distinto de payload.orderId es un evento fuera de contrato")
    void aggregateIdQueNoCoincide() throws Exception {
        ObjectNode evento = ejemplo();
        evento.put("aggregateId", UUID.randomUUID().toString());

        consumidor.consumir(registro(evento.toString()), confirmacion);

        verifyNoInteractions(pagos);
        verify(confirmacion).acknowledge();
    }

    @Test
    @DisplayName("una version del contrato que no entiende se descarta")
    void versionNoSoportada() throws Exception {
        ObjectNode evento = ejemplo();
        evento.put("eventVersion", 2);

        consumidor.consumir(registro(evento.toString()), confirmacion);

        verifyNoInteractions(pagos);
        verify(confirmacion).acknowledge();
    }

    @Test
    @DisplayName("un payload con campos fuera del contrato o sin transactionReference se descarta")
    void payloadFueraDeContrato() throws Exception {
        ObjectNode extra = ejemplo();
        ((ObjectNode) extra.get("payload")).put("campoInventado", "x");
        ObjectNode sinReferencia = ejemplo();
        ((ObjectNode) sinReferencia.get("payload")).remove("transactionReference");

        consumidor.consumir(registro(extra.toString()), confirmacion);
        consumidor.consumir(registro(sinReferencia.toString()), confirmacion);

        verifyNoInteractions(pagos);
        verify(confirmacion, org.mockito.Mockito.times(2)).acknowledge();
    }

    @Test
    @DisplayName("un pedido inexistente NO confirma el offset: el error sube para que Kafka reintente")
    void pedidoInexistenteSeReintenta() throws Exception {
        when(pagos.registrarPagoAprobado(any())).thenThrow(new OrderNotFoundException(UUID.randomUUID()));

        assertThatThrownBy(() -> consumidor.consumir(registro(ejemplo().toString()), confirmacion))
                .isInstanceOf(OrderNotFoundException.class);

        verify(confirmacion, never()).acknowledge();
    }

    private ObjectNode ejemplo() throws Exception {
        return (ObjectNode) jackson.readTree(Files.readString(EJEMPLO));
    }

    private static ConsumerRecord<String, String> registro(String valor) {
        return new ConsumerRecord<>("payments.events", 0, 0L, "clave", valor);
    }
}
