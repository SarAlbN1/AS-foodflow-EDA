package com.foodflow.notification.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodflow.notification.domain.Notification;
import com.foodflow.notification.infrastructure.persistence.NotificationRepository;

/**
 * Consulta de las notificaciones de un pedido (HU-305).
 *
 * <p>Se resuelve <strong>solo</strong> desde Notification DB (criterio 4): no pregunta a Order
 * Service si el pedido existe. Por eso un pedido desconocido no es un error sino una lista vacia,
 * igual que un pedido cuyo pago todavia no se ha resuelto ({@code contracts/api/openapi.yaml}).
 *
 * <p>Es de solo lectura y no comparte nada con {@link NotificationApplicationService}, que es
 * quien crea las notificaciones a partir de los eventos de pago.
 */
@Service
public class NotificationQueryService {

    private final NotificationRepository notificaciones;

    public NotificationQueryService(NotificationRepository notificaciones) {
        this.notificaciones = notificaciones;
    }

    /** Notificaciones del pedido, de la mas reciente a la mas antigua; posiblemente ninguna. */
    @Transactional(readOnly = true)
    public List<Notification> notificacionesDelPedido(UUID orderId) {
        return notificaciones.findByOrderIdOrderByCreatedAtDescIdDesc(orderId);
    }
}
