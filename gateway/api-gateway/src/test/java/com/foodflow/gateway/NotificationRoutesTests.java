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
 * Criterios de HU-402 con el gateway completo frente a un Order Service y un Notification Service
 * simulados: la consulta de notificaciones llega solo a Notification Service, y su respuesta
 * vuelve sin que el gateway la complete con datos de pedidos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationRoutesTests {

    private static final String PEDIDO = "3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10";

    private static final String LISTA = """
            [{"id":"8b1f6d24-59ac-4a1e-9f0c-6d1c2b3a4e5f","orderId":"3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10","status":"ENVIADA"}]""";

    private static final String PROBLEMA_400 = """
            {"type":"https://foodflow.local/problems/validation-error","status":400,"code":"VALIDATION_ERROR"}""";

    private static final List<String> llegaronANotification = new CopyOnWriteArrayList<>();
    private static final List<String> llegaronAOrder = new CopyOnWriteArrayList<>();

    private static final HttpServer notificationService = simulado(NotificationRoutesTests::atenderNotification);
    private static final HttpServer orderService = simulado(intercambio -> {
        llegaronAOrder.add(intercambio.getRequestMethod() + " " + intercambio.getRequestURI().getPath());
        responder(intercambio, 200, "application/json", "{}");
    });

    private final HttpClient cliente = HttpClient.newHttpClient();

    @LocalServerPort
    private int puerto;

    @DynamicPropertySource
    static void rutas(DynamicPropertyRegistry registro) {
        registro.add("foodflow.gateway.notification-service-url",
                () -> "http://localhost:" + notificationService.getAddress().getPort());
        registro.add("foodflow.gateway.order-service-url",
                () -> "http://localhost:" + orderService.getAddress().getPort());
    }

    @AfterAll
    static void detener() {
        notificationService.stop(0);
        orderService.stop(0);
    }

    @BeforeEach
    void limpiar() {
        llegaronANotification.clear();
        llegaronAOrder.clear();
    }

    @Test
    @DisplayName("CA1 y CA2: GET /orders/{id}/notifications llega solo a Notification Service y su lista vuelve intacta")
    void enrutaSoloANotificationService() throws Exception {
        HttpResponse<String> respuesta = get("/orders/" + PEDIDO + "/notifications");

        assertThat(llegaronANotification).containsExactly("GET /orders/" + PEDIDO + "/notifications");
        assertThat(llegaronAOrder).as("el gateway no consulta a Order Service ni combina datos").isEmpty();
        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).isEqualTo(LISTA);
    }

    @Test
    @DisplayName("CA2: una lista vacia de Notification Service vuelve como [] y no como 404")
    void listaVaciaSinReglasDelGateway() throws Exception {
        HttpResponse<String> respuesta = get("/orders/00000000-0000-4000-8000-000000000000/notifications");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).isEqualTo("[]");
        assertThat(llegaronAOrder).isEmpty();
    }

    @Test
    @DisplayName("el 400 de Notification Service se preserva con su Problem Details")
    void preservaEl400() throws Exception {
        HttpResponse<String> respuesta = get("/orders/no-es-uuid/notifications");

        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                tipo -> assertThat(tipo).startsWith("application/problem+json"));
        assertThat(respuesta.body()).isEqualTo(PROBLEMA_400);
    }

    @Test
    @DisplayName("la ruta de pedidos sigue yendo a Order Service y no a Notification Service")
    void noConfundeLasRutas() throws Exception {
        get("/orders/" + PEDIDO);

        assertThat(llegaronAOrder).containsExactly("GET /orders/" + PEDIDO);
        assertThat(llegaronANotification).isEmpty();
    }

    private HttpResponse<String> get(String ruta) throws IOException, InterruptedException {
        return cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Imita a Notification Service: un id que no es UUID responde 400, el pedido conocido su lista
     * y cualquier otro {@code []}.
     */
    private static void atenderNotification(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        llegaronANotification.add(intercambio.getRequestMethod() + " " + ruta);
        if (ruta.contains("no-es-uuid")) {
            responder(intercambio, 400, "application/problem+json", PROBLEMA_400);
        } else if (ruta.contains(PEDIDO)) {
            responder(intercambio, 200, "application/json", LISTA);
        } else {
            responder(intercambio, 200, "application/json", "[]");
        }
    }

    private static HttpServer simulado(com.sun.net.httpserver.HttpHandler manejador) {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/", manejador);
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException("no se pudo iniciar el servicio simulado", e);
        }
    }

    private static void responder(HttpExchange intercambio, int codigo, String tipo, String cuerpo)
            throws IOException {
        intercambio.getRequestBody().readAllBytes();
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", tipo);
        intercambio.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }
}
