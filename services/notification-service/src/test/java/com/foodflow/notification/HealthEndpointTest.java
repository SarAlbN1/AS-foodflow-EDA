package com.foodflow.notification;

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
 * HU-604 — health checks de Notification Service.
 *
 * <p>Comprueba el contrato de {@code docs/wiki/03-contratos/api-rest.md}. La prueba corre sin
 * infraestructura: se fija el dialecto y se desactiva la validacion de esquema para que el
 * contexto arranque tambien cuando el servicio tenga su base, sin necesitar que este levantada.
 * Esas propiedades son inertes mientras el servicio no use JPA.
 *
 * <p>Por eso no se afirma que el estado agregado sea {@code UP}: sin sus dependencias el
 * servicio esta {@code DOWN} y eso es lo correcto. Lo que si se afirma es la distincion del
 * criterio 2: {@code liveness} responde {@code 200} aunque una dependencia no este disponible.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
        })
class HealthEndpointTest {

    @Value("${local.server.port}")
    private int port;

    /** CA-1: el servicio expone un health endpoint y responde con un estado, nunca con 404. */
    @Test
    @DisplayName("CA-1: /actuator/health responde con el estado del servicio")
    void healthRespondeConUnEstado() {
        ResponseEntity<String> respuesta = get("/actuator/health");

        assertThat(respuesta.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(respuesta.getBody()).contains("\"status\"");
    }

    /** CA-2: liveness responde por la aplicacion iniciada, al margen de sus dependencias. */
    @Test
    @DisplayName("CA-2: liveness responde 200 aunque una dependencia esencial no responda")
    void livenessNoDependeDeLasDependencias() {
        assertThat(get("/actuator/health/liveness").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** CA-2: readiness se consulta por separado de liveness. */
    @Test
    @DisplayName("CA-2: readiness se expone como endpoint propio")
    void readinessSeExponePorSeparado() {
        assertThat(get("/actuator/health/readiness").getStatusCode())
                .isIn(HttpStatus.OK, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** CA-3: el cuerpo nombra los componentes pero no expone su detalle. */
    @Test
    @DisplayName("CA-3: el health no revela detalles ni credenciales")
    void healthNoRevelaDetallesNiCredenciales() {
        String cuerpo = get("/actuator/health").getBody();

        assertThat(cuerpo).doesNotContain("details");
        assertThat(cuerpo).doesNotContainIgnoringCase("password");
        assertThat(cuerpo).doesNotContainIgnoringCase("jdbc:");
        assertThat(cuerpo).doesNotContainIgnoringCase("notification_user");
    }

    /** CA-3: ningun otro endpoint de Actuator queda publicado. */
    @Test
    @DisplayName("CA-3: solo se expone el grupo health")
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
