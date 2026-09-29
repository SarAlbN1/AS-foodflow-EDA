package com.foodflow.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.foodflow.notification.application.DeliveryFailure;
import com.foodflow.notification.application.DeliveryOutcome;
import com.foodflow.notification.domain.Notification;
import com.foodflow.notification.domain.NotificationChannel;

/**
 * Criterios 1, 2, 3, 4 y 6 de HU-302 contra un <strong>servidor HTTP real</strong> del JDK.
 *
 * <p>No se simula el cliente: la peticion sale por la red, con su cliente, sus timeouts y sus
 * reintentos de verdad, y la prueba observa lo que llega al otro lado. El proveedor es el unico
 * tercero del sistema y esta fuera de su limite, asi que sustituirlo por un servidor controlado es
 * legitimo; lo que no se sustituye es nada nuestro. La entrega contra el proveedor simulado de
 * HU-306 la comprueba {@code ProviderDeliveryIntegrationTest}.
 */
class ProviderNotificationSenderTests {

    /** Lo que recibio el proveedor, en orden. */
    record Recibida(String cuerpo, String claveDeIdempotencia) {
    }

    private static final List<Recibida> recibidas = new CopyOnWriteArrayList<>();

    /** Qué responde el proveedor al intento n (1-based); una espera si devuelve null. */
    private static volatile IntFunction<Integer> respuesta = intento -> 202;
    private static volatile Duration demora = Duration.ZERO;

    private static final AtomicInteger intentos = new AtomicInteger();
    private static final HttpServer proveedor = iniciarProveedor();

    @BeforeEach
    void limpiar() {
        recibidas.clear();
        intentos.set(0);
        respuesta = intento -> 202;
        demora = Duration.ZERO;
    }

    @AfterAll
    static void detener() {
        proveedor.stop(0);
    }

    @Test
    @DisplayName("criterios 1 y 2: el proveedor recibe canal, destino, contenido y correlationId")
    void entregaElMensajeCompleto() {
        Notification notificacion = notificacion();

        DeliveryOutcome resultado = enviar(notificacion, url());

        assertThat(resultado.aceptado()).isTrue();
        assertThat(resultado.attempts()).isEqualTo(1);
        assertThat(resultado.referencia()).isPresent();
        assertThat(peticionesDe(notificacion)).hasSize(1);
        assertThat(peticionesDe(notificacion).getFirst().cuerpo())
                .contains("\"channel\":\"EMAIL\"")
                .contains("\"destination\":\"ana@foodflow.test\"")
                .contains("\"content\":\"Tu pago de 45.900,00 COP fue aprobado.\"")
                .contains("\"correlationId\":\"corr-1\"");
    }

    @Test
    @DisplayName("el informe exige enviar el notificationId como clave de idempotencia")
    void enviaLaClaveDeIdempotencia() {
        Notification notificacion = notificacion();

        enviar(notificacion, url());

        // peticionesDe filtra por la cabecera, asi que encontrar la peticion es la comprobacion:
        // sin la clave, o con otra, la lista saldria vacia.
        assertThat(peticionesDe(notificacion))
                .as("peticion con Idempotency-Key = " + notificacion.id())
                .hasSize(1);
    }

    @Test
    @DisplayName("criterio 6: un fallo transitorio se reintenta y el envio acaba aceptado")
    void reintentaElFalloTransitorio() {
        // Mismo comportamiento que *@flaky.test del proveedor simulado.
        respuesta = intento -> intento <= 2 ? 503 : 202;
        Notification notificacion = notificacion();

        DeliveryOutcome resultado = enviar(notificacion, url());

        assertThat(resultado.aceptado()).isTrue();
        assertThat(resultado.attempts()).isEqualTo(3);
        assertThat(peticionesDe(notificacion)).hasSize(3);
    }

    @Test
    @DisplayName("criterio 6: los reintentos son finitos y terminan en un resultado controlado")
    void agotaLosReintentos() {
        // Mismo comportamiento que *@fail.test.
        respuesta = intento -> 503;
        Notification notificacion = notificacion();

        DeliveryOutcome resultado = enviar(notificacion, url());

        assertThat(resultado.aceptado()).isFalse();
        assertThat(resultado.failure()).isEqualTo(DeliveryFailure.PROVEEDOR_NO_DISPONIBLE);
        assertThat(resultado.attempts()).isEqualTo(3);
        assertThat(peticionesDe(notificacion)).hasSize(3);
    }

    @Test
    @DisplayName("un 4xx no se reintenta: repetirlo produciria el mismo rechazo")
    void noReintentaElRechazo() {
        respuesta = intento -> 400;
        Notification notificacion = notificacion();

        DeliveryOutcome resultado = enviar(notificacion, url());

        assertThat(resultado.failure()).isEqualTo(DeliveryFailure.PROVEEDOR_RECHAZO_EL_MENSAJE);
        assertThat(resultado.attempts()).isEqualTo(1);
        assertThat(peticionesDe(notificacion))
                .as("un 4xx no se reintenta, asi que el proveedor recibe una sola peticion")
                .hasSize(1);
    }

    @Test
    @DisplayName("criterio 3: una respuesta mas lenta que el timeout de lectura no cuelga el envio")
    void elTiempoDeLecturaSeAgota() {
        // Mismo comportamiento que *@slow.test: responde despues del tiempo de lectura.
        demora = Duration.ofMillis(700);

        DeliveryOutcome resultado = enviar(notificacion(), url(), Duration.ofMillis(200));

        assertThat(resultado.failure()).isEqualTo(DeliveryFailure.TIEMPO_DE_ESPERA_AGOTADO);
        assertThat(resultado.attempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("criterio 4: un proveedor inalcanzable no pierde el mensaje en silencio")
    void elProveedorInalcanzableProduceUnResultado() {
        // Puerto sin nada escuchando: el fallo es de conexion, no de respuesta.
        DeliveryOutcome resultado = enviar(notificacion(), "http://localhost:1");

        assertThat(resultado.aceptado()).isFalse();
        assertThat(resultado.failure()).isEqualTo(DeliveryFailure.ERROR_DE_CONEXION);
        assertThat(resultado.attempts()).isEqualTo(3);
    }

    /**
     * Peticiones de <strong>esta</strong> notificacion. Filtrar por su clave de idempotencia hace
     * cada prueba independiente de lo que otra dejara en vuelo: el cliente abandona la peticion al
     * agotarse el tiempo de lectura, pero el proveedor la atiende de todos modos.
     */
    private static List<Recibida> peticionesDe(Notification notificacion) {
        return recibidas.stream()
                .filter(r -> notificacion.id().toString().equals(r.claveDeIdempotencia()))
                .toList();
    }

    private static DeliveryOutcome enviar(Notification notificacion, String url) {
        return enviar(notificacion, url, Duration.ofSeconds(3));
    }

    private static DeliveryOutcome enviar(Notification notificacion, String url, Duration lectura) {
        // Esperas cortas: la politica se comprueba por el numero de intentos, no por el reloj.
        return new ProviderNotificationSender(url, Duration.ofSeconds(2), lectura, 3,
                Duration.ofMillis(10), 2).enviar(notificacion, "corr-1");
    }

    private static Notification notificacion() {
        return Notification.pendiente(UUID.randomUUID(), UUID.randomUUID(), NotificationChannel.EMAIL,
                "ana@foodflow.test", "Tu pago de 45.900,00 COP fue aprobado.");
    }

    private static String url() {
        return "http://localhost:" + proveedor.getAddress().getPort();
    }

    private static HttpServer iniciarProveedor() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            // Con el ejecutor por omision el servidor atiende en un solo hilo: la peticion que el
            // cliente abandona por timeout seguiria encolada y se registraria en la prueba
            // siguiente. Un proveedor real atiende en paralelo, asi que este tambien.
            servidor.setExecutor(Executors.newCachedThreadPool());
            servidor.createContext("/v1/messages", ProviderNotificationSenderTests::atender);
            servidor.start();
            return servidor;
        } catch (IOException fallo) {
            throw new IllegalStateException("no se pudo iniciar el proveedor de prueba", fallo);
        }
    }

    private static void atender(HttpExchange intercambio) throws IOException {
        int intento = intentos.incrementAndGet();
        recibidas.add(new Recibida(
                new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                intercambio.getRequestHeaders().getFirst("Idempotency-Key")));

        if (!demora.isZero()) {
            try {
                Thread.sleep(demora);
            } catch (InterruptedException interrumpida) {
                Thread.currentThread().interrupt();
            }
        }

        int codigo = respuesta.apply(intento);
        byte[] cuerpo = codigo == 202
                ? ("{\"providerReference\":\"MOCK-" + intento + "\"}").getBytes(StandardCharsets.UTF_8)
                : "{\"title\":\"Proveedor no disponible\"}".getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(codigo, cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }
}
