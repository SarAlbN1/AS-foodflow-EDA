package com.foodflow.payment.application;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Genera la referencia de transaccion que acompana a cada pago (HU-202, criterio 4) y que
 * viaja en {@code PaymentApproved.payload.transactionReference}.
 *
 * <p>Formato {@code TXN-<yyyyMMdd>-<8 primeros digitos del orderId>}: es rastreable a simple
 * vista hasta el pedido y la fecha, y cabe de sobra en los 100 caracteres del contrato. Como
 * {@code order_id} es unico en Payment DB, hay a lo sumo un pago por pedido y la referencia no
 * se repite.
 *
 * <p>El prototipo no integra una pasarela real (ADR-10), asi que no hay identificador externo
 * que copiar: esta referencia es del propio servicio y no se presenta como de un tercero.
 */
@Component
public class TransactionReferences {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    /** Referencia del intento de cobro de un pedido en un instante dado (UTC). */
    public String nueva(UUID orderId, Instant momento) {
        return "TXN-%s-%s".formatted(FECHA.format(momento), orderId.toString().substring(0, 8));
    }
}
