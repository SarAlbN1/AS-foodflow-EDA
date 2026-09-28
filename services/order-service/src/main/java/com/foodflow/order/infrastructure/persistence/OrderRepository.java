package com.foodflow.order.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.foodflow.order.domain.Order;

/**
 * Acceso a la tabla {@code orders} de Order DB. Ningun otro servicio usa este repositorio
 * ni esta base (reglas arquitectonicas 3 y 12).
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {
}
