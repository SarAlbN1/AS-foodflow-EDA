package com.foodflow.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * HU-301, criterio 1: el consumidor esta realmente suscrito a {@code payments.events} en su grupo.
 *
 * <p>Las pruebas que invocan al consumidor a mano verifican el contrato y el filtrado, pero no
 * que Spring lo haya conectado a Kafka. Si faltara la configuracion automatica, el bean
 * existiria, esas pruebas pasarian y el servicio no consumiria nada en ejecucion real.
 *
 * <p>No necesita broker: los contenedores se crean al construir el contexto y aqui no se arrancan.
 */
@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
class PaymentResultListenerRegistrationTest {

    @Autowired
    private KafkaListenerEndpointRegistry registro;

    @Test
    @DisplayName("CA-1: hay un contenedor escuchando payments.events en su propio grupo")
    void elListenerQuedaRegistrado() {
        assertThat(registro.getListenerContainers())
                .as("sin contenedores nadie consume payments.events, por muchos @KafkaListener que haya")
                .hasSize(1);

        MessageListenerContainer contenedor = registro.getListenerContainers().iterator().next();

        assertThat(contenedor.getContainerProperties().getTopics()).containsExactly("payments.events");
        // Grupo distinto del de Order Service: los dos reciben el resultado de forma independiente.
        assertThat(contenedor.getGroupId()).isEqualTo("notification-service.payments");
    }
}
