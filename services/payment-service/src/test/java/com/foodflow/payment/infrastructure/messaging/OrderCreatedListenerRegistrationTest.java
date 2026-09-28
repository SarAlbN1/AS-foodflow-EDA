package com.foodflow.payment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * HU-201, criterio 1: el consumidor esta realmente suscrito a {@code orders.events} en su grupo.
 *
 * <p>Las pruebas que invocan al consumidor a mano verifican el contrato y el filtrado, pero no
 * que Spring lo haya conectado a Kafka. Si falta la configuracion automatica, el bean existe,
 * esas pruebas pasan y el servicio no consume nada en ejecucion real. Esta prueba mira el
 * registro de contenedores, que es lo que determina si alguien escucha de verdad.
 *
 * <p>Comprobado que detecta el fallo: con {@code spring-kafka} en lugar de
 * {@code spring-boot-starter-kafka} no existe ni el bean {@link KafkaListenerEndpointRegistry}.
 *
 * <p>No necesita broker: los contenedores se crean al construir el contexto y aqui no se
 * arrancan.
 */
@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=false",
    // Inertes mientras el servicio no use JPA; desde HU-202 evitan que el contexto necesite
    // Payment DB levantada solo para comprobar el registro del listener.
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
class OrderCreatedListenerRegistrationTest {

    @Autowired
    private KafkaListenerEndpointRegistry registro;

    @Test
    @DisplayName("CA-1: hay un contenedor escuchando orders.events en el grupo payment-service.orders")
    void elListenerQuedaRegistrado() {
        assertThat(registro.getListenerContainers())
                .as("sin contenedores nadie consume orders.events, por muchos @KafkaListener que haya")
                .hasSize(1);

        MessageListenerContainer contenedor = registro.getListenerContainers().iterator().next();

        assertThat(contenedor.getContainerProperties().getTopics()).containsExactly("orders.events");
        assertThat(contenedor.getGroupId()).isEqualTo("payment-service.orders");
    }
}
