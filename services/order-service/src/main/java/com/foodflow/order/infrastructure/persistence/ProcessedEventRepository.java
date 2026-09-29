package com.foodflow.order.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.foodflow.order.domain.ProcessedEvent;

/**
 * Acceso a {@code processed_events} de Order DB (ADR-09). Solo lo usa Order Service (regla 2).
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
