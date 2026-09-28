package com.foodflow.gateway;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
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
 *
 * <p>El Order Service simulado es un {@link ServerSocket} abierto que nunca hace {@code accept()}:
 * el sistema operativo completa la conexion y la encola, pero nadie responde. Es el caso de un
 * servicio atascado o arrancando, y solo lo corta el {@code read-timeout} del cliente HTTP. El
 * socket se mantiene abierto durante toda la clase, asi que ningun otro proceso puede tomar el
 * puerto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.http.clients.read-timeout=500ms")
class DependencyUnavailableTests {

    private static final ServerSocket servicioQueNoResponde = abrirSinAceptar();

    @LocalServerPort
    private int puerto;

    @DynamicPropertySource
    static void rutas(DynamicPropertyRegistry registro) {
        registro.add("foodflow.gateway.order-service-url",
                () -> "http://127.0.0.1:" + servicioQueNoResponde.getLocalPort());
    }

    @AfterAll
    static void cerrar() throws IOException {
        servicioQueNoResponde.close();
    }

    @Test
    @DisplayName("un Order Service que acepta la conexion y no responde produce 503 al vencer el timeout")
    void servicioSinRespuesta() throws Exception {
        HttpResponse<String> respuesta = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/orders/3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10"))
                        .header("X-Correlation-Id", "55555555-5555-5555-5555-555555555555")
                        // Si el gateway no aplicara su timeout, la prueba fallaria aqui en vez de colgarse.
                        .timeout(Duration.ofSeconds(10))
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

    private static ServerSocket abrirSinAceptar() {
        try {
            return new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
