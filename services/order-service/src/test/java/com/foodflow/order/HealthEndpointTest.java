package com.foodflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * HU-604 — health checks de Order Service.
 *
 * Comprueba el contrato de docs/wiki/03-contratos/api-rest.md: GET /actuator/health
 * responde 200 cuando el servicio y sus dependencias esenciales están disponibles.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest {

    @Value("${local.server.port}")
    private int port;

    /** CA-1: el servicio expone un health endpoint y responde 200 con estado UP. */
    @Test
    void healthRespondeDosCientosConEstadoUp() {
        ResponseEntity<String> respuesta = get("/actuator/health");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("\"status\":\"UP\"");
    }

    /** CA-2: liveness (la aplicación arrancó) y readiness (sus dependencias responden) se consultan por separado. */
    @Test
    void livenessYReadinessSeExponenPorSeparado() {
        assertThat(get("/actuator/health/liveness").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/actuator/health/readiness").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** CA-3: el cuerpo nombra los componentes pero no expone su detalle, que es donde irían credenciales. */
    @Test
    void healthNoRevelaDetallesNiCredenciales() {
        String cuerpo = get("/actuator/health").getBody();

        assertThat(cuerpo).doesNotContain("details");
        assertThat(cuerpo).doesNotContainIgnoringCase("password");
        assertThat(cuerpo).doesNotContainIgnoringCase("jdbc:");
    }

    /** CA-3: ningún otro endpoint de Actuator queda publicado. */
    @Test
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
