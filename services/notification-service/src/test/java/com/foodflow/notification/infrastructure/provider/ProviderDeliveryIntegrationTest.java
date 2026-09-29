package com.foodflow.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.foodflow.notification.application.DeliveryFailure;
import com.foodflow.notification.application.DeliveryOutcome;
import com.foodflow.notification.application.NotificationSender;
import com.foodflow.notification.domain.Notification;
import com.foodflow.notification.domain.NotificationChannel;

/**
 * HU-302 contra el <strong>proveedor simulado real</strong> de HU-306, por HTTP y por la red, con
 * el adaptador tal como lo construye Spring a partir de {@code application.properties}.
 *
 * <p>Es la prueba que cierra los criterios: los modos de fallo por destino
 * ({@code docs/wiki/03-contratos/proveedor-notificaciones.md}) no se pueden provocar con un doble
 * sin que el doble se convierta en la afirmacion que se quiere comprobar.
 *
 * <p>Necesita el proveedor levantado y {@code NOTIFICATION_PROVIDER_URL} apuntando a el:
 *
 * <pre>
 * set -a &amp;&amp; . ./.env &amp;&amp; set +a
 * docker compose -f infrastructure/compose/docker-compose.yml up -d notification-provider
 * export NOTIFICATION_PROVIDER_URL="http://localhost:${NOTIFICATION_PROVIDER_HOST_PORT:-8090}"
 * </pre>
 *
 * <p>Se omite si la variable no esta definida, para que {@code ./mvnw verify} siga funcionando sin
 * infraestructura, igual que las pruebas contra las bases.
 */
@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
@EnabledIfEnvironmentVariable(named = "NOTIFICATION_PROVIDER_URL", matches = ".+")
class ProviderDeliveryIntegrationTest {

    @Autowired
    private NotificationSender proveedor;

    @Test
    @DisplayName("criterios 1 y 2: un destino normal se entrega y el proveedor devuelve su referencia")
    void entregaAlDestinoNormal() {
        DeliveryOutcome resultado = proveedor.enviar(notificacionPara("ana@foodflow.test"), correlacion());

        assertThat(resultado.aceptado()).isTrue();
        assertThat(resultado.attempts()).isEqualTo(1);
        assertThat(resultado.referencia()).get().asString().startsWith("MOCK-");
    }

    @Test
    @DisplayName("criterio 6: *@flaky.test falla dos veces y el tercer intento lo entrega")
    void reintentaHastaEntregar() {
        // El contador del proveedor es por destino y vive en memoria: un destino nuevo en cada
        // ejecucion, o la segunda vez la prueba empezaria con los intentos ya consumidos.
        DeliveryOutcome resultado = proveedor.enviar(
                notificacionPara(UUID.randomUUID() + "@flaky.test"), correlacion());

        assertThat(resultado.aceptado()).isTrue();
        assertThat(resultado.attempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("criterios 4 y 6: *@fail.test agota los reintentos y devuelve un fallo controlado")
    void agotaLosReintentosYNoPierdeElMensaje() {
        DeliveryOutcome resultado = proveedor.enviar(notificacionPara("ana@fail.test"), correlacion());

        assertThat(resultado.aceptado()).isFalse();
        assertThat(resultado.failure()).isEqualTo(DeliveryFailure.PROVEEDOR_NO_DISPONIBLE);
        assertThat(resultado.attempts()).isEqualTo(3);
        assertThat(resultado.referencia()).isEmpty();
    }

    @Test
    @DisplayName("criterio 3: *@slow.test tarda mas que el tiempo de lectura y el envio no se cuelga")
    void elTiempoDeLecturaSeAgota() {
        // El proveedor responde tras MOCK_SLOW_DELAY (5 s por omision) y la lectura corta a los 3 s.
        DeliveryOutcome resultado = proveedor.enviar(notificacionPara("ana@slow.test"), correlacion());

        assertThat(resultado.aceptado()).isFalse();
        assertThat(resultado.failure()).isEqualTo(DeliveryFailure.TIEMPO_DE_ESPERA_AGOTADO);
    }

    private static Notification notificacionPara(String destino) {
        return Notification.pendiente(UUID.randomUUID(), UUID.randomUUID(), NotificationChannel.EMAIL,
                destino, "Tu pago de 45.900,00 COP fue aprobado. Estamos preparando tu pedido.");
    }

    private static String correlacion() {
        return UUID.randomUUID().toString();
    }
}
