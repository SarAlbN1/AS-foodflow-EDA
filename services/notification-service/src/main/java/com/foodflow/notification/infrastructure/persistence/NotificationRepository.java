package com.foodflow.notification.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.foodflow.notification.domain.Notification;

/**
 * Acceso a la tabla {@code notifications} de Notification DB. Ningun otro servicio usa este
 * repositorio ni esta base (regla arquitectonica 2).
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Notificaciones de un pedido, de la mas reciente a la mas antigua (HU-305). Usa el indice
     * {@code idx_notifications_order_id}; un pedido sin notificaciones devuelve lista vacia.
     *
     * <p>El desempate por {@code id} hace el orden estable cuando dos notificaciones comparten
     * {@code createdAt}: sin el, la base puede devolverlas en cualquier orden entre consultas.
     */
    List<Notification> findByOrderIdOrderByCreatedAtDescIdDesc(UUID orderId);
}
