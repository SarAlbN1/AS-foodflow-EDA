package com.foodflow.payment.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.foodflow.payment.domain.Payment;

/**
 * Acceso a la tabla {@code payments} de Payment DB. Ningun otro servicio usa este repositorio
 * ni esta base (regla arquitectonica 2).
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Pago ya registrado para un pedido. {@code order_id} es unico, asi que como mucho hay uno;
     * es la consulta que evita cobrar dos veces el mismo pedido al reprocesar un evento.
     */
    Optional<Payment> findByOrderId(UUID orderId);
}
