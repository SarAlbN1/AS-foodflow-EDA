package com.foodflow.order.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * HU-103, criterio 1: el productor esta realmente configurado.
 *
 * <p>Las pruebas que invocan al publicador con un {@code KafkaTemplate} simulado verifican el
 * contrato del evento, pero no que Spring haya montado el productor. Si faltara la
 * configuracion automatica de Kafka, el bean del publicador existiria igual, esas pruebas
 * pasarian y el servicio no publicaria nada en ejecucion real. Esta prueba mira el
 * {@code KafkaTemplate} y el topico resuelto, que es lo que determina si se publica de verdad.
 *
 * <p>No necesita broker: el productor se crea al construir el contexto y aqui no se envia nada.
 */
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
class OrderEventPublisherWiringTest {

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafka;

    @Autowired(required = false)
    private OrderEventPublisher publicador;

    @Value("${foodflow.kafka.orders-topic}")
    private String ordersTopic;

    @Test
    @DisplayName("CA-1: hay un KafkaTemplate y un publicador sobre orders.events")
    void elProductorQuedaConfigurado() {
        assertThat(kafka)
                .as("sin KafkaTemplate no se publica nada, por mucho que exista el publicador")
                .isNotNull();
        assertThat(publicador).isNotNull();
        assertThat(ordersTopic).isEqualTo("orders.events");
    }

    @Test
    @DisplayName("CA-4: el productor usa acks=all e idempotencia")
    void garantiasDePublicacion() {
        var props = kafka.getProducerFactory().getConfigurationProperties();

        assertThat(props.get("acks")).isEqualTo("all");
        assertThat(String.valueOf(props.get("enable.idempotence"))).isEqualTo("true");
    }
}
