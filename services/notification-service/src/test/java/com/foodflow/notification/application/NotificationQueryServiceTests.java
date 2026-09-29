package com.foodflow.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.foodflow.notification.domain.Notification;
import com.foodflow.notification.domain.NotificationChannel;
import com.foodflow.notification.infrastructure.persistence.NotificationRepository;

/** HU-305: la consulta delega en Notification DB y no altera lo que devuelve. */
class NotificationQueryServiceTests {

    private final NotificationRepository repositorio = mock(NotificationRepository.class);
    private final NotificationQueryService consultas = new NotificationQueryService(repositorio);

    @Test
    @DisplayName("CA4: devuelve lo que hay en Notification DB para el pedido, en el mismo orden")
    void delegaEnLaBaseDelServicio() {
        UUID pedido = UUID.randomUUID();
        Notification a = Notification.pendiente(pedido, null, NotificationChannel.EMAIL, "ana@foodflow.test", "a");
        Notification b = Notification.pendiente(pedido, null, NotificationChannel.EMAIL, "ana@foodflow.test", "b");
        when(repositorio.findByOrderIdOrderByCreatedAtDesc(pedido)).thenReturn(List.of(a, b));

        assertThat(consultas.notificacionesDelPedido(pedido)).containsExactly(a, b);
    }

    @Test
    @DisplayName("CA1: sin notificaciones devuelve lista vacia, no un error")
    void sinNotificaciones() {
        UUID pedido = UUID.randomUUID();
        when(repositorio.findByOrderIdOrderByCreatedAtDesc(pedido)).thenReturn(List.of());

        assertThat(consultas.notificacionesDelPedido(pedido)).isEmpty();
    }
}
