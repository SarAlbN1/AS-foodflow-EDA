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
 * Evento ya procesado por este servicio (ADR-09, HU-601).
 *
 * <p>Kafka entrega al menos una vez, asi que el mismo {@code OrderCreated} puede llegar dos
 * veces. El {@code eventId} es la clave primaria y se escribe en la <strong>misma transaccion
 * local</strong> que el pago: o quedan las dos cosas o no queda ninguna.
 *
 * <p><strong>Por que hace falta si ya existe la clave unica de {@code payments.order_id}.</strong>
 * Esa restriccion responde a una pregunta distinta —«¿este pedido ya tiene pago?»— y protege del
 * doble cobro, que es el riesgo grave. Esta responde a «¿este evento ya se proceso?», que es lo
 * que ADR-09 exige y lo que permite distinguir una reentrega del broker de un evento nuevo que
 * casualmente repite el pedido. Las dos se quedan: la de negocio y la del evento.
 *
 * <p>Es una copia propia del patron, no una clase compartida: ningun servicio depende de codigo
 * de otro (regla arquitectonica 8).
 *
 * <p><strong>Implementa {@link Persistable}</strong> porque el identificador se asigna a mano:
 * sin esto Spring Data haria {@code merge} en lugar de {@code persist} y, al ser todas las
 * columnas {@code updatable = false}, el duplicado no escribiria nada
 * <strong>ni lanzaria excepcion</strong>.
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

    /** Fuera del mapeo: solo distingue una entidad recien construida de una leida. */
    @Transient
    private boolean nuevo = true;

    /** Constructor exigido por JPA. */
    protected ProcessedEvent() {
    }

    private ProcessedEvent(UUID eventId, String consumer, Instant processedAt) {
        this.eventId = eventId;
        this.consumer = consumer;
        this.processedAt = processedAt;
    }

    /** Marca un evento como procesado por el consumidor indicado. */
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
