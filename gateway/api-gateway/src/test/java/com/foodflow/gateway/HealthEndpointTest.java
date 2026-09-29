package com.foodflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Punto abierto A-10, resuelto en HU-607 — health check del API Gateway.
 *
 * <p>Mismo contrato que los servicios (HU-604, {@code docs/wiki/03-contratos/api-rest.md}). El
 * gateway no tiene dependencias propias (ni base ni Kafka), asi que su estado es {@code UP} en
 * cuanto arranca: no consulta a los servicios a los que enruta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    @DisplayName("/actuator/health responde UP sin depender de los servicios")
    void healthRespondeUp() {
        ResponseEntity<String> respuesta = get("/actuator/health");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("liveness y readiness se exponen por separado")
    void livenessYReadiness() {
        assertThat(get("/actuator/health/liveness").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/actuator/health/readiness").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("el health no revela detalles")
    void healthNoRevelaDetalles() {
        assertThat(get("/actuator/health").getBody()).doesNotContain("details");
    }

    @Test
    @DisplayName("solo se expone el grupo health")
    void ningunOtroEndpointDeActuatorEstaExpuesto() {
        assertThat(get("/actuator/env").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/actuator/configprops").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> get(String ruta) {
        return RestClient.create("http://localhost:" + port)
                .get()
                .uri(ruta)
                .exchange((peticion, respuesta) -> new ResponseEntity<>(
                        new String(respuesta.getBody().readAllBytes(), StandardCharsets.UTF_8),
                        respuesta.getStatusCode()));
    }
}
