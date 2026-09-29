package com.foodflow.notification.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.foodflow.notification.domain.ProcessedEvent;

/** Acceso a la tabla {@code processed_events} de Notification DB (ADR-09). */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
