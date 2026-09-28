package com.foodflow.order.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.foodflow.order.domain.IdempotencyKey;

/**
 * Acceso a la tabla {@code idempotency_keys} de Order DB. Ningun otro servicio usa este
 * repositorio ni esta base (regla arquitectonica 2).
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
}
