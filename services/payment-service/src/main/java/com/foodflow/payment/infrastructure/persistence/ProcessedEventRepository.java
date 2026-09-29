package com.foodflow.payment.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.foodflow.payment.domain.ProcessedEvent;

/** Acceso a la tabla {@code processed_events} de Payment DB (ADR-09, HU-601). */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
