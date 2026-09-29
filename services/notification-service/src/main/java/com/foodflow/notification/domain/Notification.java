package com.foodflow.notification.domain;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

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
 *
 * <p><strong>Implementa {@link Persistable}</strong> porque el identificador se asigna a mano.
 * Sin esto Spring Data no puede deducir que la entidad es nueva, asume que no lo es y hace
 * {@code merge} en lugar de {@code persist}: un {@code SELECT} de mas por cada insercion. La
 * fila se acaba escribiendo igual, porque el identificador es aleatorio y ese {@code SELECT}
 * nunca encuentra nada; lo que se gana aqui es el coste y la coherencia con las otras entidades
 * de identificador asignado, mas el caso en que la entidad llegara <em>detached</em>.
 *
 * <p><strong>No es el caso de {@link ProcessedEvent}.</strong> Alli el identificador es el
 * {@code eventId} del evento, que por reentrega de Kafka <strong>si llega repetido</strong>: con
 * {@code merge} y las columnas {@code updatable = false}, el duplicado no escribiria nada
 * <strong>ni lanzaria excepcion</strong>, y la notificacion duplicada se crearia igual. Ahi
 * {@code Persistable} sostiene la idempotencia; aqui solo evita trabajo.
 */
@Entity
@Table(name = "notifications")
public class Notification implements Persistable<UUID> {

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

    /** Fuera del mapeo: solo distingue una entidad recien construida de una leida. */
    @Transient
    private boolean nueva = true;

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

    @PostLoad
    @PostPersist
    void marcarComoPersistida() {
        this.nueva = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return nueva;
    }

    /**
     * Pasa la notificacion a {@link NotificationStatus#ENVIADA} y registra los intentos que
     * costo (HU-303, criterios 1 y 2).
     *
     * <p><strong>Solo desde {@code PENDIENTE}.</strong> La maquina de estados de
     * {@code comportamiento-del-flujo.md} admite {@code PENDIENTE -> ENVIADA} y
     * {@code PENDIENTE -> FALLIDA}, y cualquier otra transicion se ignora sin error y sin
     * evento. Devolver {@code false} en vez de lanzar es lo que permite ignorarla: una
     * reentrega no puede convertirse en un fallo del consumidor ni en un segundo
     * {@code NotificationSent}.
     *
     * @param attempts intentos realizados contra el proveedor, al menos 1
     * @return {@code true} si la transicion ocurrio
     */
    public boolean marcarEnviada(int attempts) {
        if (status != NotificationStatus.PENDIENTE) {
            return false;
        }
        if (attempts < 1) {
            throw new IllegalArgumentException("una notificacion enviada tiene al menos un intento");
        }
        this.status = NotificationStatus.ENVIADA;
        this.attempts = attempts;
        this.failureCode = null;
        this.updatedAt = Instant.now();
        return true;
    }

    /**
     * Pasa la notificacion a {@link NotificationStatus#FALLIDA} con el motivo del proveedor
     * (HU-304, criterio 1).
     *
     * <p><strong>Es un resultado de negocio, no una averia.</strong> El proveedor contesto y dijo
     * que no; el evento de pago no va a DLQ y su offset se confirma (regla 10). Por eso esta
     * transicion es tan valida como {@link #marcarEnviada(int)} y no un error.
     *
     * <p>Misma regla que ella: solo desde {@code PENDIENTE}, y si no se ignora devolviendo
     * {@code false}.
     *
     * @param failureCode motivo del fallo, del catalogo de
     *                    {@code docs/wiki/03-contratos/proveedor-notificaciones.md}
     * @param attempts    intentos realizados contra el proveedor, al menos 1
     * @return {@code true} si la transicion ocurrio
     */
    public boolean marcarFallida(String failureCode, int attempts) {
        if (status != NotificationStatus.PENDIENTE) {
            return false;
        }
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("una notificacion fallida necesita su motivo");
        }
        if (attempts < 1) {
            throw new IllegalArgumentException("una notificacion fallida tiene al menos un intento");
        }
        this.status = NotificationStatus.FALLIDA;
        this.failureCode = failureCode;
        this.attempts = attempts;
        this.updatedAt = Instant.now();
        return true;
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
