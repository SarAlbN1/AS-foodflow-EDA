package com.foodflow.order.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.foodflow.order.config.EventJsonConfig;
import com.foodflow.order.domain.NotificationChannel;
import com.foodflow.order.domain.Order;
import com.foodflow.order.domain.PaymentToken;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * HU-103 — publicacion de {@code OrderCreated}.
 *
 * <p>Las pruebas invocan al publicador con un {@code KafkaTemplate} simulado: verifican el
 * contrato del evento, la clave de particion y el comportamiento ante un fallo de publicacion,
 * sin depender de un broker. El recorrido con Kafka real lo cubre HU-605.
 */
class OrderEventPublisherTests {

    private static final String TOPICO = "orders.events";

    /**
     * Ejemplo canonico del contrato. Se lee del archivo en vez de copiarlo para que un cambio en
     * {@code contracts/events/v1/} rompa esta prueba en lugar de pasar inadvertido.
     */
    private static final Path EJEMPLO_ORDER_CREATED =
            Path.of("../../contracts/events/v1/examples/validos/order-created.json");

    private KafkaTemplate<String, String> kafka;
    private ObjectMapper jackson;
    private OrderEventPublisher publicador;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void prepararPublicador() {
        kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        jackson = new EventJsonConfig().eventObjectMapper();
        publicador = new OrderEventPublisher(kafka, jackson, TOPICO);
    }

    @Test
    @DisplayName("CA-2: el evento lleva los campos del contrato y la clave es el orderId")
    void publicaElEventoDelContrato() {
        Order pedido = pedido();

        publicador.publicarOrderCreated(pedido, UUID.fromString("1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9"));

        JsonNode evento = jackson.readTree(cuerpoPublicado());
        assertThat(evento.get("eventType").asString()).isEqualTo("OrderCreated");
        assertThat(evento.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(evento.get("eventId").asString()).isNotBlank();
        assertThat(evento.get("correlationId").asString())
                .isEqualTo("1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9");
        // Regla 11 y ADR-04: aggregateId es SIEMPRE el orderId.
        assertThat(evento.get("aggregateId").asString()).isEqualTo(pedido.id().toString());
        assertThat(evento.get("payload").get("orderId").asString()).isEqualTo(pedido.id().toString());

        JsonNode payload = evento.get("payload");
        assertThat(payload.get("customerReference").asString()).isEqualTo("CLI-000123");
        assertThat(payload.get("currency").asString()).isEqualTo("COP");
        assertThat(payload.get("paymentToken").asString()).isEqualTo("PAY-OK");
        assertThat(payload.get("notificationContact").get("channel").asString()).isEqualTo("EMAIL");
        assertThat(payload.get("notificationContact").get("destination").asString())
                .isEqualTo("cliente@foodflow.test");
        assertThat(new BigDecimal(payload.get("total").asString())).isEqualByComparingTo("45900.00");
    }

    @Test
    @DisplayName("CA-2: la clave del mensaje es el orderId, que es la clave de particion")
    void laClaveEsElOrderId() {
        Order pedido = pedido();

        publicador.publicarOrderCreated(pedido, UUID.randomUUID());

        ArgumentCaptor<String> clave = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(TOPICO), clave.capture(), anyString());
        assertThat(clave.getValue()).isEqualTo(pedido.id().toString());
    }

    @Test
    @DisplayName("el envelope tiene exactamente los campos del ejemplo versionado del contrato")
    void noSeDesviaDelContrato() throws Exception {
        publicador.publicarOrderCreated(pedido(), UUID.randomUUID());

        JsonNode publicado = jackson.readTree(cuerpoPublicado());
        JsonNode canonico = jackson.readTree(Files.readString(EJEMPLO_ORDER_CREATED));

        assertThat(nombres(publicado)).isEqualTo(nombres(canonico));
        assertThat(nombres(publicado.get("payload"))).isEqualTo(nombres(canonico.get("payload")));
        assertThat(nombres(publicado.get("payload").get("notificationContact")))
                .isEqualTo(nombres(canonico.get("payload").get("notificationContact")));
    }

    @Test
    @DisplayName("occurredAt sale en UTC con sufijo Z, como exige el patron del envelope")
    void fechaEnUtc() {
        publicador.publicarOrderCreated(pedido(), UUID.randomUUID());

        String occurredAt = jackson.readTree(cuerpoPublicado()).get("occurredAt").asString();
        assertThat(occurredAt).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$");
    }

    @Test
    @DisplayName("CA-5: un fallo al publicar se registra y no se propaga, el pedido ya esta creado")
    void unFalloAlPublicarNoRompeLaCreacion() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker caido")));

        // No lanza: el pedido ya esta persistido y propagar el error devolveria un fallo por
        // algo que si ocurrio. ADR-08 acepta que quede en CREADO sin evento.
        publicador.publicarOrderCreated(pedido(), UUID.randomUUID());

        verify(kafka).send(eq(TOPICO), anyString(), anyString());
    }

    @Test
    @DisplayName("dos publicaciones del mismo pedido llevan eventId distinto")
    void cadaEventoTieneSuPropioIdentificador() {
        Order pedido = pedido();

        publicador.publicarOrderCreated(pedido, UUID.randomUUID());
        publicador.publicarOrderCreated(pedido, UUID.randomUUID());

        ArgumentCaptor<String> cuerpos = ArgumentCaptor.forClass(String.class);
        verify(kafka, org.mockito.Mockito.times(2)).send(anyString(), anyString(), cuerpos.capture());
        String primero = jackson.readTree(cuerpos.getAllValues().get(0)).get("eventId").asString();
        String segundo = jackson.readTree(cuerpos.getAllValues().get(1)).get("eventId").asString();
        assertThat(primero).isNotEqualTo(segundo);
    }

    @Test
    @DisplayName("CA-1 y CA-3: con transaccion activa no se publica nada hasta el commit")
    void esperaAlCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            publicador.publicarOrderCreated(pedido(), UUID.randomUUID());

            // Todavia no: la transaccion que persiste el pedido no ha hecho commit.
            verify(kafka, never()).send(anyString(), anyString(), anyString());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(kafka).send(eq(TOPICO), anyString(), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("CA-3: si la transaccion se deshace, el evento no se publica nunca")
    void unRollbackNoPublica() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            publicador.publicarOrderCreated(pedido(), UUID.randomUUID());

            // Se deshace la transaccion: afterCommit no llega a ejecutarse.
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(kafka, never()).send(anyString(), anyString(), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private String cuerpoPublicado() {
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(TOPICO), anyString(), cuerpo.capture());
        return cuerpo.getValue();
    }

    private static java.util.List<String> nombres(JsonNode nodo) {
        java.util.List<String> nombres = new java.util.ArrayList<>();
        nodo.propertyNames().forEach(nombres::add);
        java.util.Collections.sort(nombres);
        return nombres;
    }

    private static Order pedido() {
        return Order.crear("CLI-000123", NotificationChannel.EMAIL, "cliente@foodflow.test",
                PaymentToken.PAY_OK, new BigDecimal("45900.00"));
    }
}
