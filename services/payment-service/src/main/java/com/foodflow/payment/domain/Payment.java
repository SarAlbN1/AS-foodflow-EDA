package com.foodflow.payment.domain;

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
 * Pago de un pedido. Payment Service es su unico propietario (regla arquitectonica 2).
 *
 * <p>Es un resultado transaccional independiente del estado del pedido: Order Service se entera
 * por {@code PaymentApproved} o {@code PaymentRejected}, nunca leyendo esta tabla.
 *
 * <p>El mapeo debe coincidir con {@code infrastructure/postgres/payment-db/01-schema.sql}: el
 * esquema lo crean esos scripts y Hibernate solo lo valida. La columna {@code order_id} es
 * UNICA, que es lo que impide cobrar dos veces el mismo pedido si {@code OrderCreated} se
 * reprocesa (HU-202, criterio 5).
 *
 * <p>El snapshot de contacto no se guarda aqui: la tabla no tiene columnas de contacto y el
 * dato solo viaja del evento de entrada al evento de pago (ADR-11).
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false, unique = true)
    private UUID orderId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Constructor exigido por JPA. */
    protected Payment() {
    }

    private Payment(UUID id, UUID orderId, BigDecimal amount, PaymentStatus status,
            String reasonCode, String transactionReference, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.reasonCode = reasonCode;
        this.transactionReference = transactionReference;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Resuelve el pago de un pedido segun el token determinista del pedido (ADR-10) y lo deja
     * listo para persistir. El resultado se conoce aqui mismo: no hay estado pendiente.
     *
     * <p>El importe llega validado por el contrato del evento (mayor que cero y con dos
     * decimales como maximo), asi que ajustar la escala a 2 no puede perder informacion.
     *
     * @param transactionReference referencia identificable del intento de cobro; nunca nula,
     *                             tambien cuando el pago se rechaza, para poder rastrearlo
     */
    public static Payment resolver(UUID orderId, BigDecimal amount, PaymentToken token,
            String transactionReference) {
        if (transactionReference == null || transactionReference.isBlank()) {
            throw new IllegalArgumentException("transactionReference es obligatoria");
        }
        boolean aprobado = token == PaymentToken.PAY_OK;
        Instant ahora = Instant.now();
        return new Payment(
                UUID.randomUUID(),
                orderId,
                amount.setScale(2, RoundingMode.UNNECESSARY),
                aprobado ? PaymentStatus.APROBADO : PaymentStatus.RECHAZADO,
                aprobado ? null : RejectionReason.PAGO_RECHAZADO_POR_TOKEN,
                transactionReference,
                ahora,
                ahora);
    }

    public UUID id() {
        return id;
    }

    public UUID orderId() {
        return orderId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public PaymentStatus status() {
        return status;
    }

    /** Motivo del rechazo; nulo cuando el pago fue aprobado. */
    public String reasonCode() {
        return reasonCode;
    }

    public String transactionReference() {
        return transactionReference;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean aprobado() {
        return status == PaymentStatus.APROBADO;
    }
}
