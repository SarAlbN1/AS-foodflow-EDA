package com.foodflow.gateway;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Criterios de HU-401 contra un Order Service simulado con el servidor HTTP del JDK: el gateway
 * arranca completo en un puerto aleatorio y la prueba observa lo que llega al servicio y lo que
 * vuelve al cliente. No necesita Docker ni el Order Service real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderRoutesTests {

    private static final String PEDIDO = """
            {"id":"3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10","status":"CREADO"}""";

    private static final String PROBLEMA_404 = """
            {"type":"https://foodflow.local/problems/not-found","status":404,"code":"NOT_FOUND"}""";

    private static final String PROBLEMA_400 = """
            {"type":"https://foodflow.local/problems/validation-error","status":400,"code":"VALIDATION_ERROR"}""";

    /** Lo que recibio el Order Service simulado, en orden. */
    record Recibida(String metodo, String ruta, String idempotencyKey, String cuerpo) {
    }

    private static final List<Recibida> recibidas = new CopyOnWriteArrayList<>();

    private static final HttpServer orderService = iniciarOrderServiceSimulado();

    private final HttpClient cliente = HttpClient.newHttpClient();

    @LocalServerPort
    private int puerto;

    @DynamicPropertySource
    static void rutas(DynamicPropertyRegistry registro) {
        registro.add("foodflow.gateway.order-service-url",
                () -> "http://localhost:" + orderService.getAddress().getPort());
    }

    @AfterAll
    static void detener() {
        orderService.stop(0);
    }

    @BeforeEach
    void limpiar() {
        recibidas.clear();
    }

    @Test
    @DisplayName("CA1 y CA5: POST /orders llega a Order Service con el cuerpo y la Idempotency-Key intactos")
    void enrutaLaCreacion() throws Exception {
        String cuerpo = "{\"customerReference\":\"PED-0001\",\"total\":45000.00}";

        HttpResponse<String> respuesta = enviar(HttpRequest.newBuilder(url("/orders"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "7c9e6679-7425-40de-944b-e07fc1f90ae7")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)));

        assertThat(recibidas).containsExactly(
                new Recibida("POST", "/orders", "7c9e6679-7425-40de-944b-e07fc1f90ae7", cuerpo));
        assertThat(respuesta.statusCode()).isEqualTo(201);
        assertThat(respuesta.headers().firstValue("Location"))
                .hasValue("/orders/3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10");
        assertThat(respuesta.body()).isEqualTo(PEDIDO);
    }

    @Test
    @DisplayName("CA2: GET /orders/{id} llega a Order Service y su 200 vuelve sin cambios")
    void enrutaLaConsulta() throws Exception {
        HttpResponse<String> respuesta = enviar(HttpRequest.newBuilder(
                url("/orders/3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10")).GET());

        assertThat(recibidas).extracting(Recibida::metodo, Recibida::ruta)
                .containsExactly(tuple("GET", "/orders/3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10"));
        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).isEqualTo(PEDIDO);
    }

    @Test
    @DisplayName("CA4: el 404 de Order Service se preserva con su Problem Details")
    void preservaEl404() throws Exception {
        HttpResponse<String> respuesta = enviar(HttpRequest.newBuilder(url("/orders/inexistente")).GET());

        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                tipo -> assertThat(tipo).startsWith("application/problem+json"));
        assertThat(respuesta.body()).isEqualTo(PROBLEMA_404);
    }

    @Test
    @DisplayName("CA3 y CA4: el gateway no valida; la entrada invalida llega al servicio y su 400 se preserva")
    void noAplicaReglasDeNegocio() throws Exception {
        String invalido = "{\"total\":-1,\"notificationChannel\":\"SMS\"}";

        HttpResponse<String> respuesta = enviar(HttpRequest.newBuilder(url("/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(invalido)));

        assertThat(recibidas).extracting(Recibida::cuerpo).containsExactly(invalido);
        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(respuesta.body()).isEqualTo(PROBLEMA_400);
    }

    @Test
    @DisplayName("solo se enrutan las operaciones de la API minima: otras rutas no llegan a Order Service")
    void noEnrutaOperacionesFueraDelContrato() throws Exception {
        HttpResponse<String> pagos = enviar(HttpRequest.newBuilder(url("/payments")).GET());
        HttpResponse<String> borrar = enviar(HttpRequest.newBuilder(
                url("/orders/3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10")).DELETE());

        assertThat(pagos.statusCode()).isEqualTo(404);
        assertThat(borrar.statusCode()).isIn(404, 405);
        assertThat(recibidas).isEmpty();
    }

    private HttpResponse<String> enviar(HttpRequest.Builder peticion) throws IOException, InterruptedException {
        return cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI url(String ruta) {
        return URI.create("http://localhost:" + puerto + ruta);
    }

    /**
     * Imita a Order Service: {@code POST /orders} con {@code total} negativo responde 400, el resto
     * de {@code POST} responde 201; {@code GET /orders/inexistente} responde 404 y el resto, 200.
     */
    private static HttpServer iniciarOrderServiceSimulado() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/", OrderRoutesTests::atender);
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException("no se pudo iniciar el Order Service simulado", e);
        }
    }

    private static void atender(HttpExchange intercambio) throws IOException {
        String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String ruta = intercambio.getRequestURI().getPath();
        recibidas.add(new Recibida(intercambio.getRequestMethod(), ruta,
                intercambio.getRequestHeaders().getFirst("Idempotency-Key"), cuerpo));

        if ("POST".equals(intercambio.getRequestMethod())) {
            if (cuerpo.contains("\"total\":-1")) {
                responder(intercambio, 400, "application/problem+json", PROBLEMA_400);
            } else {
                intercambio.getResponseHeaders().add("Location", "/orders/3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10");
                responder(intercambio, 201, "application/json", PEDIDO);
            }
        } else if (ruta.endsWith("/inexistente")) {
            responder(intercambio, 404, "application/problem+json", PROBLEMA_404);
        } else {
            responder(intercambio, 200, "application/json", PEDIDO);
        }
    }

    private static void responder(HttpExchange intercambio, int codigo, String tipo, String cuerpo)
            throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", tipo);
        intercambio.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }
}
