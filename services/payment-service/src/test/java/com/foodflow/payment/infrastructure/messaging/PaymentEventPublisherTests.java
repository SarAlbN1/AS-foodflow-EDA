package com.foodflow.payment.infrastructure.messaging;

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

import com.foodflow.payment.application.StartPaymentCommand;
import com.foodflow.payment.config.EventJsonConfig;
import com.foodflow.payment.domain.NotificationChannel;
import com.foodflow.payment.domain.NotificationContact;
import com.foodflow.payment.domain.Payment;
import com.foodflow.payment.domain.PaymentToken;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * HU-203 — publicacion de {@code PaymentApproved}.
 *
 * <p>Las pruebas invocan al publicador con un {@code KafkaTemplate} simulado: verifican el
 * contrato del evento, la clave de particion y el momento de la publicacion, sin depender de un
 * broker. El recorrido con Kafka real lo cubre HU-605.
 */
class PaymentEventPublisherTests {

    private static final String TOPICO = "payments.events";
    private static final UUID ORDER_ID = UUID.fromString("3f8b1c2e-5a47-4d9b-8e10-7c2a6b4f9d31");
    private static final UUID CORRELACION = UUID.fromString("1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9");

    /**
     * Ejemplo canonico del contrato. Se lee del archivo en vez de copiarlo para que un cambio en
     * {@code contracts/events/v1/} rompa esta prueba en lugar de pasar inadvertido.
     */
    private static final Path EJEMPLO_APPROVED =
            Path.of("../../contracts/events/v1/examples/validos/payment-approved.json");

    private KafkaTemplate<String, String> kafka;
    private ObjectMapper jackson;
    private PaymentEventPublisher publicador;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void prepararPublicador() {
        kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        jackson = new EventJsonConfig().eventObjectMapper();
        publicador = new PaymentEventPublisher(kafka, jackson, TOPICO);
    }

    @Test
    @DisplayName("CA-2: el evento lleva los campos del contrato, incluido el contacto recibido")
    void publicaElEventoDelContrato() {
        Payment pago = aprobado();

        publicador.publicarResultado(pago, orden());

        JsonNode evento = jackson.readTree(cuerpoPublicado());
        assertThat(evento.get("eventType").asString()).isEqualTo("PaymentApproved");
        assertThat(evento.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(evento.get("correlationId").asString()).isEqualTo(CORRELACION.toString());
        // Regla 11 y ADR-04: aggregateId es SIEMPRE el orderId, no el paymentId.
        assertThat(evento.get("aggregateId").asString()).isEqualTo(ORDER_ID.toString());

        JsonNode payload = evento.get("payload");
        assertThat(payload.get("paymentId").asString()).isEqualTo(pago.id().toString());
        assertThat(payload.get("orderId").asString()).isEqualTo(ORDER_ID.toString());
        assertThat(payload.get("currency").asString()).isEqualTo("COP");
        assertThat(payload.get("transactionReference").asString()).isEqualTo(pago.transactionReference());
        assertThat(new BigDecimal(payload.get("amount").asString())).isEqualByComparingTo("45900.00");
        // ADR-11: el contacto no esta en Payment DB; viaja del evento de entrada al de salida.
        assertThat(payload.get("notificationContact").get("channel").asString()).isEqualTo("EMAIL");
        assertThat(payload.get("notificationContact").get("destination").asString())
                .isEqualTo("cliente@foodflow.test");
    }

    @Test
    @DisplayName("CA-4: la clave del mensaje es el orderId, para que Order y Notification lo reciban en orden")
    void laClaveEsElOrderId() {
        publicador.publicarResultado(aprobado(), orden());

        ArgumentCaptor<String> clave = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(TOPICO), clave.capture(), anyString());
        assertThat(clave.getValue()).isEqualTo(ORDER_ID.toString());
    }

    @Test
    @DisplayName("el envelope tiene exactamente los campos del ejemplo versionado del contrato")
    void noSeDesviaDelContrato() throws Exception {
        publicador.publicarResultado(aprobado(), orden());

        JsonNode publicado = jackson.readTree(cuerpoPublicado());
        JsonNode canonico = jackson.readTree(Files.readString(EJEMPLO_APPROVED));

        assertThat(nombres(publicado)).isEqualTo(nombres(canonico));
        assertThat(nombres(publicado.get("payload"))).isEqualTo(nombres(canonico.get("payload")));
        assertThat(nombres(publicado.get("payload").get("notificationContact")))
                .isEqualTo(nombres(canonico.get("payload").get("notificationContact")));
    }

    @Test
    @DisplayName("occurredAt sale en UTC con sufijo Z, como exige el patron del envelope")
    void fechaEnUtc() {
        publicador.publicarResultado(aprobado(), orden());

        assertThat(jackson.readTree(cuerpoPublicado()).get("occurredAt").asString())
                .matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$");
    }

    @Test
    @DisplayName("CA-3: con transaccion activa no se publica nada hasta el commit")
    void esperaAlCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            publicador.publicarResultado(aprobado(), orden());

            verify(kafka, never()).send(anyString(), anyString(), anyString());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(kafka).send(eq(TOPICO), anyString(), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("CA-3: si la persistencia se deshace, el evento no se publica nunca")
    void unRollbackNoPublica() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            publicador.publicarResultado(aprobado(), orden());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(kafka, never()).send(anyString(), anyString(), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("un pago rechazado no produce PaymentApproved; su evento es HU-204")
    void unPagoRechazadoNoAnunciaAprobacion() {
        Payment rechazado = Payment.resolver(ORDER_ID, new BigDecimal("45900.00"),
                PaymentToken.PAY_FAIL, "TXN-20260927-" + ORDER_ID);

        publicador.publicarResultado(rechazado, orden());

        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("un fallo al publicar se registra y no se propaga: el pago ya esta persistido")
    void unFalloAlPublicarNoRompeElPago() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker caido")));

        publicador.publicarResultado(aprobado(), orden());

        verify(kafka).send(eq(TOPICO), anyString(), anyString());
    }

    private String cuerpoPublicado() {
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(TOPICO), anyString(), cuerpo.capture());
        return cuerpo.getValue();
    }

    private static List<String> nombres(JsonNode nodo) {
        List<String> nombres = new ArrayList<>();
        nodo.propertyNames().forEach(nombres::add);
        Collections.sort(nombres);
        return nombres;
    }

    private static Payment aprobado() {
        return Payment.resolver(ORDER_ID, new BigDecimal("45900.00"), PaymentToken.PAY_OK,
                "TXN-20260927-" + ORDER_ID);
    }

    private static StartPaymentCommand orden() {
        return new StartPaymentCommand(UUID.randomUUID(), CORRELACION, ORDER_ID,
                new BigDecimal("45900.00"), "COP", PaymentToken.PAY_OK,
                new NotificationContact(NotificationChannel.EMAIL, "cliente@foodflow.test"));
    }
}
