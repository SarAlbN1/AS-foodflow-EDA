package com.foodflow.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.foodflow.notification.domain.Notification;
import com.foodflow.notification.domain.NotificationChannel;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Criterios 3 y 4 de HU-303: el envelope y el payload que salen a {@code notifications.events},
 * comprobados campo por campo contra
 * {@code contracts/events/v1/notification-sent.schema.json}.
 *
 * <p>Aqui el {@code KafkaTemplate} esta simulado a proposito: lo que se comprueba es la
 * <strong>forma del mensaje</strong> y la clave de particion, no que Kafka lo acepte. Que el
 * evento cruza un broker real lo comprueba la verificacion de extremo a extremo y lo automatiza
 * HU-605.
 */
class NotificationEventPublisherTests {

    private static final String TOPICO = "notifications.events";
    private static final UUID CORRELACION = UUID.fromString("1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9");

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private KafkaTemplate<String, String> kafka;
    private NotificationEventPublisher publicador;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepararPublicador() {
        kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        publicador = new NotificationEventPublisher(kafka, JSON, TOPICO);
    }

    @Test
    @DisplayName("criterio 3: NotificationSent sale a notifications.events con el orderId como clave")
    void publicaEnElTopicoConLaClaveDeParticion() {
        Notification notificacion = enviada();

        publicador.publicarEnviada(notificacion, "MOCK-9f3c21", CORRELACION);

        // La clave es la clave de particion (regla 11, ADR-04): los hechos de un pedido conservan
        // su orden aunque el topico tenga varias particiones.
        verify(kafka).send(eq(TOPICO), eq(notificacion.orderId().toString()), anyString());
    }

    @Test
    @DisplayName("criterio 4: el evento conserva notificationId, orderId y correlationId")
    void elEventoCumpleElContrato() {
        Notification notificacion = enviada();

        publicador.publicarEnviada(notificacion, "MOCK-9f3c21", CORRELACION);

        JsonNode evento = JSON.readTree(cuerpoPublicado());

        // Envelope comun.
        assertThat(evento.get("eventType").asString()).isEqualTo("NotificationSent");
        assertThat(evento.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(evento.get("eventId").asString()).isNotBlank();
        assertThat(evento.get("occurredAt").asString()).endsWith("Z");
        assertThat(evento.get("correlationId").asString()).isEqualTo(CORRELACION.toString());
        assertThat(evento.get("aggregateId").asString()).isEqualTo(notificacion.orderId().toString());

        // Payload: exactamente los cinco campos del esquema, que declara additionalProperties false.
        JsonNode payload = evento.get("payload");
        assertThat(payload.propertyNames()).containsExactlyInAnyOrder(
                "notificationId", "orderId", "paymentId", "channel", "providerReference");
        assertThat(payload.get("notificationId").asString()).isEqualTo(notificacion.id().toString());
        assertThat(payload.get("orderId").asString()).isEqualTo(notificacion.orderId().toString());
        assertThat(payload.get("paymentId").asString()).isEqualTo(notificacion.paymentId().toString());
        assertThat(payload.get("channel").asString()).isEqualTo("EMAIL");
        assertThat(payload.get("providerReference").asString()).isEqualTo("MOCK-9f3c21");
    }

    @Test
    @DisplayName("el evento no lleva el destino ni el contenido: no hacen falta y son dato personal")
    void noFiltraDatosPersonales() {
        publicador.publicarEnviada(enviada(), "MOCK-9f3c21", CORRELACION);

        assertThat(cuerpoPublicado())
                .doesNotContain("ana@foodflow.test")
                .doesNotContain("a***@foodflow.test")
                .doesNotContain("Tu pago");
    }

    @Test
    @DisplayName("un fallo al publicar no se propaga: el envio al cliente ya ocurrio")
    void elFalloAlPublicarNoSePropaga() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker caido")));

        // Si esto lanzara, el consumidor trataria el evento de pago como no procesado y Kafka lo
        // reentregaria, sin arreglar nada: la notificacion ya esta ENVIADA (ADR-08, regla 12).
        publicador.publicarEnviada(enviada(), "MOCK-9f3c21", CORRELACION);

        verify(kafka).send(eq(TOPICO), anyString(), anyString());
    }

    private String cuerpoPublicado() {
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(TOPICO), anyString(), cuerpo.capture());
        return cuerpo.getValue();
    }

    private static Notification enviada() {
        Notification notificacion = Notification.pendiente(UUID.randomUUID(), UUID.randomUUID(),
                NotificationChannel.EMAIL, "ana@foodflow.test",
                "Tu pago de 45.900,00 COP fue aprobado.");
        notificacion.marcarEnviada(1);
        return notificacion;
    }

    @Test
    @DisplayName("criterios 2 y 4 de HU-304: NotificationFailed lleva el motivo y los intentos")
    void publicaElFalloConSuCausa() {
        Notification notificacion = fallida();

        publicador.publicarFallida(notificacion, CORRELACION);

        JsonNode evento = JSON.readTree(cuerpoPublicado());
        assertThat(evento.get("eventType").asString()).isEqualTo("NotificationFailed");
        assertThat(evento.get("aggregateId").asString()).isEqualTo(notificacion.orderId().toString());
        assertThat(evento.get("correlationId").asString()).isEqualTo(CORRELACION.toString());

        JsonNode payload = evento.get("payload");
        assertThat(payload.propertyNames()).containsExactlyInAnyOrder(
                "notificationId", "orderId", "paymentId", "channel", "failureCode", "attempts");
        assertThat(payload.get("failureCode").asString()).isEqualTo("PROVEEDOR_NO_DISPONIBLE");
        assertThat(payload.get("attempts").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("el evento de fallo tampoco lleva el destino ni el contenido")
    void elFalloNoFiltraDatosPersonales() {
        publicador.publicarFallida(fallida(), CORRELACION);

        assertThat(cuerpoPublicado())
                .doesNotContain("ana@foodflow.test")
                .doesNotContain("Tu pago");
    }

    private static Notification fallida() {
        Notification notificacion = Notification.pendiente(UUID.randomUUID(), UUID.randomUUID(),
                NotificationChannel.EMAIL, "ana@foodflow.test",
                "Tu pago de 45.900,00 COP fue aprobado.");
        notificacion.marcarFallida("PROVEEDOR_NO_DISPONIBLE", 3);
        return notificacion;
    }
}
