package com.foodflow.order.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Clave de idempotencia de una solicitud de creacion de pedido (HU-107).
 *
 * <p>Evita que un reintento del cliente —un doble clic o un reenvio tras un fallo de red— cree
 * dos pedidos a partir de la misma solicitud HTTP. Es distinta de la idempotencia de
 * consumidores Kafka de ADR-09: aquella evita procesar dos veces el mismo evento asincrono.
 *
 * <p>El mapeo debe coincidir con {@code infrastructure/postgres/order-db/01-schema.sql}: la
 * clave es la primaria, asi que la base impide dos filas con el mismo valor aunque dos
 * solicitudes lleguen a la vez.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @Column(name = "key", nullable = false, updatable = false, length = 200)
    private String key;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Constructor exigido por JPA. */
    protected IdempotencyKey() {
    }

    private IdempotencyKey(String key, String requestHash, UUID orderId, Instant createdAt) {
        this.key = key;
        this.requestHash = requestHash;
        this.orderId = orderId;
        this.createdAt = createdAt;
    }

    /** Registra que esta clave ya produjo un pedido, con el hash de la solicitud que lo creo. */
    public static IdempotencyKey de(String key, String requestHash, UUID orderId) {
        return new IdempotencyKey(key, requestHash, orderId, Instant.now());
    }

    public String key() {
        return key;
    }

    /** Hash de la solicitud original: distingue un reintento de un conflicto. */
    public String requestHash() {
        return requestHash;
    }

    public UUID orderId() {
        return orderId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** {@code true} si la solicitud recibida es la misma que creo el pedido. */
    public boolean mismaSolicitud(String otroHash) {
        return requestHash.equals(otroHash);
    }
}
