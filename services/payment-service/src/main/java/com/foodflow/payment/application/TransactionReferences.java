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
 * <p>Formato {@code TXN-<yyyyMMdd>-<orderId>}: es rastreable a simple vista hasta el pedido y la
 * fecha, y ocupa 49 caracteres de los 100 que admite el contrato.
 *
 * <p>Lleva el {@code orderId} completo y no un prefijo suyo. Con 8 digitos hexadecimales solo
 * habria 32 bits y dos pedidos del mismo dia podrian compartir referencia, que es justo lo que
 * una referencia de transaccion no puede hacer; {@code transaction_reference} no tiene indice
 * unico que lo impida. Con el identificador entero, la unicidad se hereda de la de
 * {@code order_id}, que si es unico en Payment DB.
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
        return "TXN-%s-%s".formatted(FECHA.format(momento), orderId);
    }
}
