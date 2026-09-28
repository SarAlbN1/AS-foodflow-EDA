package com.foodflow.gateway;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CA4 de HU-401, la unica transformacion documentada: si Order Service no responde, el gateway
 * contesta {@code 503 DEPENDENCY_UNAVAILABLE} en Problem Details, sin trazas ni direccion interna.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DependencyUnavailableTests {

    @LocalServerPort
    private int puerto;

    @DynamicPropertySource
    static void rutas(DynamicPropertyRegistry registro) {
        registro.add("foodflow.gateway.order-service-url", () -> "http://localhost:" + puertoLibre());
    }

    @Test
    @DisplayName("Order Service caido produce 503 DEPENDENCY_UNAVAILABLE con el correlationId recibido")
    void servicioCaido() throws Exception {
        HttpResponse<String> respuesta = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/orders/3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10"))
                        .header("X-Correlation-Id", "55555555-5555-5555-5555-555555555555")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(respuesta.statusCode()).isEqualTo(503);
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                tipo -> assertThat(tipo).startsWith("application/problem+json"));
        assertThat(respuesta.body())
                .contains("\"code\":\"DEPENDENCY_UNAVAILABLE\"")
                .contains("\"correlationId\":\"55555555-5555-5555-5555-555555555555\"")
                .contains("\"status\":503")
                .doesNotContain("localhost")
                .doesNotContain("Exception");
    }

    /** Un puerto que nadie escucha: se abre y se cierra de inmediato. */
    private static int puertoLibre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
