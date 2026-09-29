package com.foodflow.payment.domain;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Evento ya procesado por un consumidor de Payment Service (ADR-09, HU-601).
 *
 * <p>El mapeo coincide con la tabla {@code processed_events} de
 * {@code infrastructure/postgres/payment-db/01-schema.sql}. Se escribe en la misma transaccion
 * local que el efecto de negocio: o quedan las dos cosas o ninguna.
 *
 * <p><strong>Implementa {@link Persistable}</strong> porque el identificador es el
 * {@code eventId} del evento y <strong>llega repetido</strong> cuando Kafka reentrega. Sin esto
 * Spring Data haria {@code merge}: con las columnas {@code updatable = false}, el duplicado no
 * escribiria nada ni lanzaria excepcion. Con {@code persist}, un duplicado que se colara choca con
 * la clave primaria y la transaccion se deshace. Mismo motivo que en Order y Notification Service.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent implements Persistable<UUID> {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "consumer", nullable = false, updatable = false, length = 100)
    private String consumer;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @Transient
    private boolean nuevo = true;

    protected ProcessedEvent() {
    }

    private ProcessedEvent(UUID eventId, String consumer, Instant processedAt) {
        this.eventId = eventId;
        this.consumer = consumer;
        this.processedAt = processedAt;
    }

    public static ProcessedEvent de(UUID eventId, String consumer) {
        return new ProcessedEvent(eventId, consumer, Instant.now());
    }

    @PostLoad
    @PostPersist
    void marcarComoPersistido() {
        this.nuevo = false;
    }

    @Override
    public UUID getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return nuevo;
    }

    public UUID eventId() {
        return eventId;
    }

    public String consumer() {
        return consumer;
    }

    public Instant processedAt() {
        return processedAt;
    }
}
