package com.foodflow.order.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import com.foodflow.order.domain.OrderStatus;
import com.foodflow.order.domain.PaymentToken;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Publicacion de {@code OrderStatusChanged} (HU-106) contra el ejemplo valido del contrato
 * ({@code contracts/events/v1/examples/validos/order-status-changed.json}), que se lee del archivo
 * para que un cambio en el contrato rompa esta prueba en lugar de pasar inadvertido.
 */
class OrderStatusChangedPublisherTests {

    private static final String TOPICO = "orders.events";
    private static final Path EJEMPLO =
            Path.of("../../contracts/events/v1/examples/validos/order-status-changed.json");

    private KafkaTemplate<String, String> kafka;
    private ObjectMapper jackson;
    private OrderEventPublisher publicador;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void preparar() {
        kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        jackson = new EventJsonConfig().eventObjectMapper();
        publicador = new OrderEventPublisher(kafka, jackson, TOPICO);
    }

    @Test
    @DisplayName("CA1, CA2 y CA3: orders.events, clave orderId, estados anterior y nuevo, contacto y correlationId")
    void publicaElCambio() throws Exception {
        Order pedido = pedidoPagado();
        UUID correlationId = UUID.randomUUID();

        publicador.publicarOrderStatusChanged(pedido, OrderStatus.CREADO, correlationId);

        ArgumentCaptor<String> clave = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(TOPICO), clave.capture(), cuerpo.capture());
        JsonNode evento = jackson.readTree(cuerpo.getValue());

        assertThat(clave.getValue()).isEqualTo(pedido.id().toString());
        assertThat(evento.get("eventType").asText()).isEqualTo("OrderStatusChanged");
        assertThat(evento.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(evento.get("aggregateId").asText()).isEqualTo(pedido.id().toString());
        assertThat(evento.get("correlationId").asText()).isEqualTo(correlationId.toString());
        JsonNode payload = evento.get("payload");
        assertThat(payload.get("orderId").asText()).isEqualTo(pedido.id().toString());
        assertThat(payload.get("previousStatus").asText()).isEqualTo("CREADO");
        assertThat(payload.get("newStatus").asText()).isEqualTo("PAGADO");
        assertThat(payload.get("notificationContact").get("channel").asText()).isEqualTo("EMAIL");
        assertThat(payload.get("notificationContact").get("destination").asText()).isEqualTo("cliente@foodflow.test");
    }

    @Test
    @DisplayName("CA2: el envelope y el payload tienen exactamente los campos del ejemplo del contrato")
    void noSeDesviaDelContrato() throws Exception {
        publicador.publicarOrderStatusChanged(pedidoPagado(), OrderStatus.CREADO, UUID.randomUUID());

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(TOPICO), anyString(), cuerpo.capture());
        JsonNode publicado = jackson.readTree(cuerpo.getValue());
        JsonNode canonico = jackson.readTree(Files.readString(EJEMPLO));

        assertThat(nombres(publicado)).isEqualTo(nombres(canonico));
        assertThat(nombres(publicado.get("payload"))).isEqualTo(nombres(canonico.get("payload")));
        assertThat(nombres(publicado.get("payload").get("notificationContact")))
                .isEqualTo(nombres(canonico.get("payload").get("notificationContact")));
    }

    @Test
    @DisplayName("CA1: con transaccion activa solo se publica tras el commit; un rollback no publica")
    void despuesDelCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            publicador.publicarOrderStatusChanged(pedidoPagado(), OrderStatus.CREADO, UUID.randomUUID());
            verify(kafka, never()).send(anyString(), anyString(), anyString());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(kafka, never()).send(anyString(), anyString(), anyString());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(kafka).send(eq(TOPICO), anyString(), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static Order pedidoPagado() {
        Order pedido = Order.crear("CLI-000124", NotificationChannel.EMAIL, "cliente@foodflow.test",
                PaymentToken.PAY_OK, new BigDecimal("45900.00"));
        pedido.marcarPagado();
        return pedido;
    }

    private static List<String> nombres(JsonNode nodo) {
        List<String> nombres = new ArrayList<>();
        nodo.propertyNames().forEach(nombres::add);
        Collections.sort(nombres);
        return nombres;
    }
}
