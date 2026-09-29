package com.foodflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.foodflow.notification.application.NotificationQueryService;
import com.foodflow.notification.domain.Notification;
import com.foodflow.notification.domain.NotificationChannel;
import com.foodflow.notification.infrastructure.persistence.NotificationRepository;

/**
 * Criterios 1, 2 y 4 de HU-305 contra Notification DB real: la consulta se resuelve solo desde
 * esa base, devuelve las notificaciones del pedido pedido y ninguna de otro, de la mas reciente a
 * la mas antigua, y una lista vacia cuando el pedido no tiene ninguna.
 *
 * <p>Como {@code NotificationServiceApplicationTests}: se omite si {@code NOTIFICATION_DB_URL} no
 * esta definida y deja el consumidor de Kafka parado.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@EnabledIfEnvironmentVariable(named = "NOTIFICATION_DB_URL", matches = ".+")
class NotificationQueryIntegrationTests {

    @Autowired
    private NotificationQueryService consultas;

    @Autowired
    private NotificationRepository notificaciones;

    @Test
    @DisplayName("CA2 y CA4: solo las del pedido, de la mas reciente a la mas antigua")
    void soloLasDelPedidoEnOrden() throws InterruptedException {
        UUID pedido = UUID.randomUUID();
        Notification primera = guardar(pedido, "Tu pago de 1,00 COP fue rechazado.");
        Thread.sleep(5);
        Notification segunda = guardar(pedido, "Tu pago de 1,00 COP fue aprobado.");
        Notification deOtroPedido = guardar(UUID.randomUUID(), "Tu pago de 2,00 COP fue aprobado.");

        try {
            List<Notification> delPedido = consultas.notificacionesDelPedido(pedido);

            assertThat(delPedido).extracting(Notification::id).containsExactly(segunda.id(), primera.id());
            assertThat(delPedido).allSatisfy(n -> assertThat(n.orderId()).isEqualTo(pedido));
        } finally {
            notificaciones.deleteAll(List.of(primera, segunda, deOtroPedido));
        }
    }

    @Test
    @DisplayName("CA1: un pedido sin notificaciones devuelve lista vacia")
    void pedidoSinNotificaciones() {
        assertThat(consultas.notificacionesDelPedido(UUID.randomUUID())).isEmpty();
    }

    private Notification guardar(UUID pedido, String contenido) {
        return notificaciones.saveAndFlush(Notification.pendiente(pedido, UUID.randomUUID(),
                NotificationChannel.EMAIL, "ana@foodflow.test", contenido));
    }
}
