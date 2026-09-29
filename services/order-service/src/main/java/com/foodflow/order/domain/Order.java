package com.foodflow.order.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Pedido de FoodFlow. Order Service es su unico propietario (regla arquitectonica 3).
 *
 * <p>Conserva el snapshot del contacto y del canal de notificacion (ADR-11): la notificacion
 * se envia con el contacto vigente al crear el pedido, sin volver a consultarlo.
 *
 * <p>El mapeo debe coincidir con {@code infrastructure/postgres/order-db/01-schema.sql}:
 * el esquema lo crean esos scripts y Hibernate solo lo valida.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_reference", nullable = false, length = 60)
    private String customerReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_channel", nullable = false, length = 20)
    private NotificationChannel notificationChannel;

    @Column(name = "customer_contact", nullable = false, length = 255)
    private String customerContact;

    @Column(name = "payment_token", nullable = false, length = 20)
    private PaymentToken paymentToken;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Constructor exigido por JPA. */
    protected Order() {
    }

    private Order(UUID id, String customerReference, NotificationChannel notificationChannel,
            String customerContact, PaymentToken paymentToken, BigDecimal total,
            OrderStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerReference = customerReference;
        this.notificationChannel = notificationChannel;
        this.customerContact = customerContact;
        this.paymentToken = paymentToken;
        this.total = total;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Crea un pedido nuevo: identificador propio, estado {@link OrderStatus#CREADO} y el
     * snapshot de notificacion de ADR-11. El pago lo inicia Payment Service al consumir
     * {@code OrderCreated} (regla arquitectonica 9), nunca este metodo.
     *
     * <p>El total llega ya validado (mayor que cero y con dos decimales como maximo), asi que
     * ajustar la escala a 2 no puede perder informacion.
     */
    public static Order crear(String customerReference, NotificationChannel notificationChannel,
            String customerContact, PaymentToken paymentToken, BigDecimal total) {
        Instant ahora = Instant.now();
        return new Order(UUID.randomUUID(), customerReference, notificationChannel, customerContact,
                paymentToken, total.setScale(2, RoundingMode.UNNECESSARY),
                OrderStatus.CREADO, ahora, ahora);
    }

    /**
     * Registra que el pago fue aprobado: {@code CREADO} pasa a {@code PAGADO} (HU-104).
     *
     * <p>Es la unica transicion que acepta. Cualquier otro estado de partida la deja como esta
     * y devuelve {@code false}: la maquina de estados de {@code comportamiento-del-flujo.md}
     * ignora una transicion invalida con {@code WARN}, sin error ni evento.
     *
     * @return {@code true} si el pedido cambio de estado
     */
    public boolean marcarPagado() {
        return transicionarDesdeCreado(OrderStatus.PAGADO);
    }

    /**
     * Registra que el pago fue rechazado: {@code CREADO} pasa a {@code PAGO_RECHAZADO} (HU-105).
     * Misma regla que {@link #marcarPagado()}: desde cualquier otro estado no cambia nada.
     *
     * @return {@code true} si el pedido cambio de estado
     */
    public boolean marcarPagoRechazado() {
        return transicionarDesdeCreado(OrderStatus.PAGO_RECHAZADO);
    }

    private boolean transicionarDesdeCreado(OrderStatus destino) {
        if (status != OrderStatus.CREADO) {
            return false;
        }
        status = destino;
        updatedAt = Instant.now();
        return true;
    }

    public UUID id() {
        return id;
    }

    public String customerReference() {
        return customerReference;
    }

    public NotificationChannel notificationChannel() {
        return notificationChannel;
    }

    public String customerContact() {
        return customerContact;
    }

    public PaymentToken paymentToken() {
        return paymentToken;
    }

    public BigDecimal total() {
        return total;
    }

    public OrderStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
