package com.foodflow.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/*
 * Sin broker en las pruebas de contexto: el contenedor del @KafkaListener no arranca, asi que
 * no se intenta conectar a Kafka. El recorrido con un broker real lo cubre HU-605.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class PaymentServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
