package com.foodflow.order.validation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.foodflow.order.domain.NotificationChannel;
import com.foodflow.order.domain.PaymentToken;
import com.foodflow.order.validation.OrderValidationException.Violation;

/**
 * Reglas de entrada de {@code POST /orders} (criterio 3 de HU-101 y pagina
 * {@code docs/wiki/03-contratos/api-rest.md}). Acumula todos los motivos de rechazo en un solo
 * {@code 400} en lugar de detenerse en el primero.
 *
 * <p>Los limites numericos son los de la columna {@code total NUMERIC(12,2)} de Order DB.
 */
@Component
public class OrderValidator {

    private static final int REFERENCIA_MAX = 60;
    private static final int CONTACTO_MAX = 255;
    private static final int TOTAL_ENTEROS_MAX = 10;
    private static final int TOTAL_DECIMALES_MAX = 2;

    /** Formato de correo suficiente para el prototipo: un arroba y un dominio con punto. */
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$");

    /**
     * Valida el borrador y lo convierte a tipos del dominio.
     *
     * @throws OrderValidationException si algun campo obligatorio falta o es invalido
     */
    public ValidatedOrderCommand validar(OrderDraft draft) {
        List<Violation> violations = new ArrayList<>();

        String referencia = recortar(draft.customerReference());
        if (referencia == null) {
            violations.add(new Violation("customerReference", "es obligatorio"));
        } else if (referencia.length() > REFERENCIA_MAX) {
            violations.add(new Violation("customerReference", "admite maximo " + REFERENCIA_MAX + " caracteres"));
        }

        String contacto = recortar(draft.customerContact());
        if (contacto == null) {
            violations.add(new Violation("customerContact", "es obligatorio"));
        } else if (contacto.length() > CONTACTO_MAX) {
            violations.add(new Violation("customerContact", "admite maximo " + CONTACTO_MAX + " caracteres"));
        } else if (!EMAIL.matcher(contacto).matches()) {
            violations.add(new Violation("customerContact", "debe tener formato de correo electronico"));
        }

        NotificationChannel canal = null;
        String canalCrudo = recortar(draft.notificationChannel());
        if (canalCrudo == null) {
            violations.add(new Violation("notificationChannel", "es obligatorio"));
        } else {
            canal = canalValido(canalCrudo);
            if (canal == null) {
                violations.add(new Violation("notificationChannel",
                        "el prototipo solo admite " + NotificationChannel.EMAIL));
            }
        }

        BigDecimal total = draft.total();
        if (total == null) {
            violations.add(new Violation("total", "es obligatorio"));
        } else {
            if (total.signum() <= 0) {
                violations.add(new Violation("total", "debe ser mayor que cero"));
            }
            if (total.stripTrailingZeros().scale() > TOTAL_DECIMALES_MAX) {
                violations.add(new Violation("total", "admite maximo " + TOTAL_DECIMALES_MAX + " decimales"));
            }
            if (total.precision() - total.scale() > TOTAL_ENTEROS_MAX) {
                violations.add(new Violation("total", "admite maximo " + TOTAL_ENTEROS_MAX + " digitos enteros"));
            }
        }

        PaymentToken token = null;
        String tokenCrudo = recortar(draft.paymentToken());
        if (tokenCrudo == null) {
            violations.add(new Violation("paymentToken", "es obligatorio"));
        } else {
            token = PaymentToken.desdeValor(tokenCrudo).orElse(null);
            if (token == null) {
                violations.add(new Violation("paymentToken",
                        "debe ser " + PaymentToken.PAY_OK.valor() + " o " + PaymentToken.PAY_FAIL.valor()));
            }
        }

        if (!violations.isEmpty()) {
            throw new OrderValidationException(violations);
        }

        return new ValidatedOrderCommand(referencia, contacto, canal, total, token);
    }

    private static NotificationChannel canalValido(String valor) {
        for (NotificationChannel canal : NotificationChannel.values()) {
            if (canal.name().equals(valor)) {
                return canal;
            }
        }
        return null;
    }

    /** Normaliza un texto opcional: {@code null} si viene vacio o solo con espacios. */
    private static String recortar(String valor) {
        if (valor == null) {
            return null;
        }
        String recortado = valor.trim();
        return recortado.isEmpty() ? null : recortado;
    }
}
