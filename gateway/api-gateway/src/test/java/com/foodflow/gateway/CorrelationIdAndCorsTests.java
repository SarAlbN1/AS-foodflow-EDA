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

/**
 * Criterios de HU-403 con el gateway completo frente a un Order Service simulado: que valor de
 * {@code X-Correlation-Id} llega al servicio, cual vuelve al cliente y que origenes admite CORS.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "foodflow.gateway.cors.allowed-origins=http://localhost:4200, http://foodflow.test")
class CorrelationIdAndCorsTests {

    private static final String UUID_REGEX =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    private static final String PEDIDO = "/orders/3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10";

    /** Todos los valores de X-Correlation-Id que recibio el servicio en cada solicitud. */
    private static final List<List<String>> correlacionesRecibidas = new CopyOnWriteArrayList<>();

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
        correlacionesRecibidas.clear();
    }

    @Test
    @DisplayName("CA1, CA2 y CA5: un X-Correlation-Id valido se conserva y llega al servicio")
    void conservaElValorValido() throws Exception {
        String id = "11111111-2222-4333-8444-555555555555";

        HttpResponse<String> respuesta = enviar(get(PEDIDO).header("X-Correlation-Id", id));

        assertThat(correlacionesRecibidas).containsExactly(List.of(id));
        assertThat(respuesta.headers().allValues("X-Correlation-Id")).containsExactly(id);
    }

    @Test
    @DisplayName("CA1, CA2 y CA3: si falta, se genera un UUID y es el mismo en el servicio y en la respuesta")
    void generaSiFalta() throws Exception {
        HttpResponse<String> respuesta = enviar(get(PEDIDO));

        String devuelto = respuesta.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertThat(devuelto).matches(UUID_REGEX);
        assertThat(correlacionesRecibidas).containsExactly(List.of(devuelto));
    }

    @Test
    @DisplayName("CA1: un valor que no es UUID se reemplaza y el original no llega al servicio")
    void reemplazaElValorInvalido() throws Exception {
        HttpResponse<String> respuesta = enviar(get(PEDIDO).header("X-Correlation-Id", "<script>no-uuid"));

        String devuelto = respuesta.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertThat(devuelto).matches(UUID_REGEX);
        assertThat(correlacionesRecibidas).containsExactly(List.of(devuelto));
    }

    @Test
    @DisplayName("CA3: tambien las respuestas propias del gateway llevan X-Correlation-Id")
    void respuestasDelGatewayLlevanLaCabecera() throws Exception {
        HttpResponse<String> rutaInexistente = enviar(get("/payments"));

        assertThat(rutaInexistente.statusCode()).isEqualTo(404);
        assertThat(rutaInexistente.headers().firstValue("X-Correlation-Id")).hasValueSatisfying(
                valor -> assertThat(valor).matches(UUID_REGEX));
        assertThat(correlacionesRecibidas).isEmpty();
    }

    @Test
    @DisplayName("CA4: el preflight de un origen configurado se acepta con las cabeceras de la API")
    void preflightDeOrigenPermitido() throws Exception {
        HttpResponse<String> respuesta = enviar(preflight("http://foodflow.test", "POST",
                "content-type,idempotency-key,x-correlation-id"));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://foodflow.test");
        assertThat(respuesta.headers().firstValue("Access-Control-Allow-Headers").orElseThrow().toLowerCase())
                .contains("idempotency-key", "x-correlation-id", "content-type");
        assertThat(correlacionesRecibidas).as("el preflight no se reenvia al servicio").isEmpty();
    }

    @Test
    @DisplayName("CA4 y CA3: una solicitud de origen permitido expone Location y X-Correlation-Id al navegador")
    void exponeCabecerasAlNavegador() throws Exception {
        HttpResponse<String> respuesta = enviar(get(PEDIDO).header("Origin", "http://localhost:4200"));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:4200");
        assertThat(respuesta.headers().firstValue("Access-Control-Expose-Headers").orElseThrow())
                .contains("Location", "X-Correlation-Id");
    }

    @Test
    @DisplayName("CA4: un origen no configurado se rechaza con 403 y no llega al servicio")
    void rechazaOrigenNoConfigurado() throws Exception {
        HttpResponse<String> preflight = enviar(preflight("http://malicioso.test", "POST", "content-type"));
        HttpResponse<String> directa = enviar(HttpRequest.newBuilder(url("/orders"))
                .header("Origin", "http://malicioso.test")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));

        assertThat(preflight.statusCode()).isEqualTo(403);
        assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        assertThat(directa.statusCode()).isEqualTo(403);
        assertThat(directa.headers().firstValue("X-Correlation-Id")).hasValueSatisfying(
                valor -> assertThat(valor).matches(UUID_REGEX));
        assertThat(correlacionesRecibidas).isEmpty();
    }

    private HttpRequest.Builder get(String ruta) {
        return HttpRequest.newBuilder(url(ruta)).GET();
    }

    private HttpRequest.Builder preflight(String origen, String metodo, String cabeceras) {
        return HttpRequest.newBuilder(url("/orders"))
                .header("Origin", origen)
                .header("Access-Control-Request-Method", metodo)
                .header("Access-Control-Request-Headers", cabeceras)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody());
    }

    private HttpResponse<String> enviar(HttpRequest.Builder peticion) throws IOException, InterruptedException {
        return cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI url(String ruta) {
        return URI.create("http://localhost:" + puerto + ruta);
    }

    /** Imita a Order Service: registra las cabeceras de correlacion recibidas y responde 200. */
    private static HttpServer iniciarOrderServiceSimulado() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/", CorrelationIdAndCorsTests::atender);
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException("no se pudo iniciar el Order Service simulado", e);
        }
    }

    private static void atender(HttpExchange intercambio) throws IOException {
        intercambio.getRequestBody().readAllBytes();
        List<String> recibidas = intercambio.getRequestHeaders().get("X-Correlation-Id");
        correlacionesRecibidas.add(recibidas == null ? List.of() : List.copyOf(recibidas));

        byte[] cuerpo = "{\"status\":\"CREADO\"}".getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(200, cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }
}
