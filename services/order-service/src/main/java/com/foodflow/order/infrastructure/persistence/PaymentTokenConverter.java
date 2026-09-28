package com.foodflow.order.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.foodflow.order.domain.PaymentToken;

/**
 * Traduce {@link PaymentToken} al literal que exige la restriccion {@code orders_payment_token_valido}
 * de Order DB ({@code PAY-OK} / {@code PAY-FAIL}), que no puede ser el nombre de una constante Java.
 */
@Converter(autoApply = true)
class PaymentTokenConverter implements AttributeConverter<PaymentToken, String> {

    @Override
    public String convertToDatabaseColumn(PaymentToken token) {
        return token == null ? null : token.valor();
    }

    @Override
    public PaymentToken convertToEntityAttribute(String valor) {
        if (valor == null) {
            return null;
        }
        return PaymentToken.desdeValor(valor)
                .orElseThrow(() -> new IllegalStateException("payment_token invalido en Order DB: " + valor));
    }
}
