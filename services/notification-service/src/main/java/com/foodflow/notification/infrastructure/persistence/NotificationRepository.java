package com.foodflow.notification.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.foodflow.notification.domain.Notification;

/**
 * Acceso a la tabla {@code notifications} de Notification DB. Ningun otro servicio usa este
 * repositorio ni esta base (regla arquitectonica 2).
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}
