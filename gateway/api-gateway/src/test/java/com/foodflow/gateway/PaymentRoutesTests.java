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
 * HU-205 con el gateway completo frente a Order, Payment y Notification Service simulados: la
 * consulta del pago llega solo a Payment Service, y su respuesta (tambien el {@code 404} de
 * «aun no hay pago») vuelve sin que el gateway la complete ni la reinterprete.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentRoutesTests {

    private static final String PEDIDO = "3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10";

    private static final String PAGO = """
            {"id":"c2d3e4f5-6789-4abc-8def-0123456789ab","orderId":"3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10","status":"APROBADO"}""";

    private static final String PROBLEMA_404 = """
            {"type":"https://foodflow.local/problems/not-found","status":404,"code":"NOT_FOUND"}""";

    private static final List<String> llegaronAPayment = new CopyOnWriteArrayList<>();
    private static final List<String> llegaronAOtros = new CopyOnWriteArrayList<>();

    private static final HttpServer paymentService = simulado(PaymentRoutesTests::atenderPayment);
    private static final HttpServer orderService = simulado(PaymentRoutesTests::atenderOtro);
    private static final HttpServer notificationService = simulado(PaymentRoutesTests::atenderOtro);

    private final HttpClient cliente = HttpClient.newHttpClient();

    @LocalServerPort
    private int puerto;

    @DynamicPropertySource
    static void rutas(DynamicPropertyRegistry registro) {
        registro.add("foodflow.gateway.payment-service-url",
                () -> "http://localhost:" + paymentService.getAddress().getPort());
        registro.add("foodflow.gateway.order-service-url",
                () -> "http://localhost:" + orderService.getAddress().getPort());
        registro.add("foodflow.gateway.notification-service-url",
                () -> "http://localhost:" + notificationService.getAddress().getPort());
    }

    @AfterAll
    static void detener() {
        paymentService.stop(0);
        orderService.stop(0);
        notificationService.stop(0);
    }

    @BeforeEach
    void limpiar() {
        llegaronAPayment.clear();
        llegaronAOtros.clear();
    }

    @Test
    @DisplayName("CA1 y CA4: GET /orders/{id}/payment llega solo a Payment Service y el pago vuelve intacto")
    void enrutaSoloAPaymentService() throws Exception {
        HttpResponse<String> respuesta = get("/orders/" + PEDIDO + "/payment");

        assertThat(llegaronAPayment).containsExactly("GET /orders/" + PEDIDO + "/payment");
        assertThat(llegaronAOtros).as("el gateway no consulta a otros servicios ni combina datos").isEmpty();
        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).isEqualTo(PAGO);
    }

    @Test
    @DisplayName("CA3: el 404 de «aun no hay pago» vuelve con su Problem Details, sin reinterpretarlo")
    void preservaEl404() throws Exception {
        HttpResponse<String> respuesta = get("/orders/00000000-0000-4000-8000-000000000000/payment");

        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                tipo -> assertThat(tipo).startsWith("application/problem+json"));
        assertThat(respuesta.body()).isEqualTo(PROBLEMA_404);
        assertThat(llegaronAOtros).isEmpty();
    }

    @Test
    @DisplayName("las rutas de pedido y de notificaciones no van a Payment Service")
    void noConfundeLasRutas() throws Exception {
        get("/orders/" + PEDIDO);
        get("/orders/" + PEDIDO + "/notifications");

        assertThat(llegaronAPayment).isEmpty();
        assertThat(llegaronAOtros).containsExactly(
                "GET /orders/" + PEDIDO, "GET /orders/" + PEDIDO + "/notifications");
    }

    private HttpResponse<String> get(String ruta) throws IOException, InterruptedException {
        return cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Imita a Payment Service: el pedido conocido devuelve su pago y cualquier otro {@code 404}. */
    private static void atenderPayment(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        llegaronAPayment.add(intercambio.getRequestMethod() + " " + ruta);
        if (ruta.contains(PEDIDO)) {
            responder(intercambio, 200, "application/json", PAGO);
        } else {
            responder(intercambio, 404, "application/problem+json", PROBLEMA_404);
        }
    }

    private static void atenderOtro(HttpExchange intercambio) throws IOException {
        llegaronAOtros.add(intercambio.getRequestMethod() + " " + intercambio.getRequestURI().getPath());
        responder(intercambio, 200, "application/json", "{}");
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
