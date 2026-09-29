package com.foodflow.order.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * CA1 de HU-104: el {@code @KafkaListener} queda registrado de verdad sobre {@code payments.events}.
 *
 * <p>Sin la configuracion automatica de Kafka de Spring Boot 4 (el starter y no {@code spring-kafka}
 * a secas) la anotacion se ignora en silencio: el contexto arranca y nadie consume. Es el fallo que
 * se encontro en #71, y esta prueba lo vigila aqui. Corre sin base ni broker.
 */
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
class PaymentResultListenerRegistrationTest {

    @Autowired
    private KafkaListenerEndpointRegistry registro;

    @Test
    @DisplayName("CA1: hay un contenedor escuchando payments.events en el grupo de Order Service")
    void elListenerQuedaRegistrado() {
        assertThat(registro.getListenerContainers())
                .as("sin contenedores nadie consume payments.events, por muchos @KafkaListener que haya")
                .hasSize(1);

        MessageListenerContainer contenedor = registro.getListenerContainers().iterator().next();

        assertThat(contenedor.getContainerProperties().getTopics()).containsExactly("payments.events");
        // Grupo distinto del de Notification Service: los dos reciben el resultado de forma independiente.
        assertThat(contenedor.getGroupId()).isEqualTo("order-service.payments");
    }
}
