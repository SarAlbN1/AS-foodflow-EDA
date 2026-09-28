package com.foodflow.payment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * HU-203, criterio 1: el productor esta realmente configurado.
 *
 * <p>Las pruebas que invocan al publicador con un {@code KafkaTemplate} simulado verifican el
 * contrato del evento, pero no que Spring haya montado el productor. Si faltara la
 * configuracion, el bean del publicador existiria igual, esas pruebas pasarian y el servicio no
 * publicaria nada en ejecucion real.
 *
 * <p>No necesita broker: el productor se crea al construir el contexto y aqui no se envia nada.
 */
@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
class PaymentEventPublisherWiringTest {

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafka;

    @Autowired(required = false)
    private PaymentEventPublisher publicador;

    @Value("${foodflow.kafka.payments-topic}")
    private String paymentsTopic;

    @Test
    @DisplayName("CA-1: hay un KafkaTemplate y un publicador sobre payments.events")
    void elProductorQuedaConfigurado() {
        assertThat(kafka)
                .as("sin KafkaTemplate no se publica nada, por mucho que exista el publicador")
                .isNotNull();
        assertThat(publicador).isNotNull();
        assertThat(paymentsTopic).isEqualTo("payments.events");
    }

    @Test
    @DisplayName("el productor usa acks=all e idempotencia")
    void garantiasDePublicacion() {
        var props = kafka.getProducerFactory().getConfigurationProperties();

        assertThat(props.get("acks")).isEqualTo("all");
        assertThat(String.valueOf(props.get("enable.idempotence"))).isEqualTo("true");
    }
}
