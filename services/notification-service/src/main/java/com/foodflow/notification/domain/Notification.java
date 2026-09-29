package com.foodflow.notification.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Notificacion de un pedido. Notification Service es su unico propietario (regla 2).
 *
 * <p>Nace en {@link NotificationStatus#PENDIENTE} al conocerse el resultado del pago y no se
 * envia todavia: el envio al proveedor es HU-302, y el resultado de ese envio la lleva a
 * {@code ENVIADA} (HU-303) o {@code FALLIDA} (HU-304).
 *
 * <p>El destino y el canal salen del snapshot que viaja en el evento de pago (ADR-11): este
 * servicio <strong>no consulta Order DB ni Payment DB</strong> (HU-301, criterio 5).
 *
 * <p>El mapeo debe coincidir con {@code infrastructure/postgres/notification-db/01-schema.sql}:
 * el esquema lo crean esos scripts y Hibernate solo lo valida.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "destination", nullable = false, updatable = false, length = 255)
    private String destination;

    @Column(name = "content", nullable = false, updatable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Constructor exigido por JPA. */
    protected Notification() {
    }

    private Notification(UUID id, UUID orderId, UUID paymentId, NotificationChannel channel,
            String destination, String content, NotificationStatus status, int attempts,
            Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.channel = channel;
        this.destination = destination;
        this.content = content;
        this.status = status;
        this.attempts = attempts;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Crea la notificacion pendiente de envio de un resultado de pago.
     *
     * <p>{@code attempts} arranca en 0: todavia no se ha intentado nada. El primer intento lo
     * hace HU-302.
     */
    public static Notification pendiente(UUID orderId, UUID paymentId, NotificationChannel channel,
            String destination, String content) {
        Instant ahora = Instant.now();
        return new Notification(UUID.randomUUID(), orderId, paymentId, channel, destination,
                content, NotificationStatus.PENDIENTE, 0, ahora, ahora);
    }

    public UUID id() {
        return id;
    }

    public UUID orderId() {
        return orderId;
    }

    /** Pago que origino la notificacion. */
    public UUID paymentId() {
        return paymentId;
    }

    public NotificationChannel channel() {
        return channel;
    }

    /** Dato personal: en los registros se escribe enmascarado. */
    public String destination() {
        return destination;
    }

    /** Texto que se envia al cliente. Habla del resultado del pago (D-6). */
    public String content() {
        return content;
    }

    public NotificationStatus status() {
        return status;
    }

    /** Motivo del fallo; nulo mientras la notificacion no sea {@code FALLIDA}. */
    public String failureCode() {
        return failureCode;
    }

    public int attempts() {
        return attempts;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
