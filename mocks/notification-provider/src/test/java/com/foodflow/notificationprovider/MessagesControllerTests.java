package com.foodflow.notificationprovider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Demuestra cada modo del contrato del proveedor contra el mock arrancado en un puerto aleatorio. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"mock.slow-delay=400ms", "mock.flaky-failures=2"})
class MessagesControllerTests {

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void destinoNormalRespondeAcceptedConProviderReference() throws Exception {
        HttpResponse<String> response = send(message("ana@example.com"), Duration.ofSeconds(5));

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).contains("\"providerReference\":\"MOCK-");
    }

    @Test
    void destinoFailRespondeServiceUnavailableSiempre() throws Exception {
        for (int i = 0; i < 3; i++) {
            HttpResponse<String> response = send(message("ana@fail.test"), Duration.ofSeconds(5));
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.body()).contains("Proveedor no disponible");
        }
    }

    @Test
    void destinoFlakyFallaDosVecesYLuegoAcepta() throws Exception {
        String body = message("luis@flaky.test");

        assertThat(send(body, Duration.ofSeconds(5)).statusCode()).isEqualTo(503);
        assertThat(send(body, Duration.ofSeconds(5)).statusCode()).isEqualTo(503);
        assertThat(send(body, Duration.ofSeconds(5)).statusCode()).isEqualTo(202);
        assertThat(send(body, Duration.ofSeconds(5)).statusCode()).isEqualTo(202);
    }

    @Test
    void elContadorFlakyEsPorDestino() throws Exception {
        assertThat(send(message("uno@flaky.test"), Duration.ofSeconds(5)).statusCode()).isEqualTo(503);
        assertThat(send(message("dos@flaky.test"), Duration.ofSeconds(5)).statusCode()).isEqualTo(503);
    }

    @Test
    void destinoSlowRespondeDespuesDeLaEsperaConfigurada() throws Exception {
        long start = System.nanoTime();
        HttpResponse<String> response = send(message("eva@slow.test"), Duration.ofSeconds(5));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(400));
    }

    @Test
    void destinoSlowHaceExpirarUnClienteConTimeoutMenor() {
        assertThatThrownBy(() -> send(message("eva@slow.test"), Duration.ofMillis(100)))
                .isInstanceOf(HttpTimeoutException.class);
    }

    @Test
    void cuerpoIncompletoRespondeBadRequestEnProblemDetails() throws Exception {
        HttpResponse<String> response = send("{\"channel\":\"EMAIL\",\"content\":\"hola\"}", Duration.ofSeconds(5));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
    }

    private static String message(String destination) {
        return """
                {"channel":"EMAIL","destination":"%s","content":"Pago aprobado","correlationId":"6f1c2c1e-1111-4f5e-9a0b-000000000001"}
                """.formatted(destination);
    }

    private HttpResponse<String> send(String body, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/messages"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
