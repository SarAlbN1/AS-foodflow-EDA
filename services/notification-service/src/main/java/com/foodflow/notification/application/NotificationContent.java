package com.foodflow.notification.application;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Redacta el texto que se envia al cliente.
 *
 * <p><strong>Habla del resultado del pago, nunca del estado del pedido</strong>
 * ({@code docs/wiki/03-contratos/proveedor-notificaciones.md}, decision D-6). Notification
 * Service reacciona al pago en paralelo con Order Service, asi que la notificacion puede salir
 * <em>antes</em> de que el pedido muestre su estado final, e incluso aunque Order Service no
 * llegue a procesar su copia del evento. Un mensaje que hablara del pedido podria afirmar algo
 * que todavia no es cierto; uno que habla del pago es cierto siempre, porque el pago ya ocurrio
 * cuando el evento se publico.
 *
 * <p><strong>No incluye el destino</strong> ({@code contracts/api/openapi.yaml}): el texto se
 * expone por el API y meter el correo dentro reabriria por la puerta de atras la exposicion que
 * el contrato evita enmascarando {@code destination}.
 *
 * <p>Tampoco incluye la referencia del pedido: no viaja en el evento de pago, y pedirla
 * obligaria a consultar Order DB, que es justo lo que prohibe el criterio 5 de HU-301.
 */
public final class NotificationContent {

    private NotificationContent() {
    }

    /** Texto de un pago aprobado. */
    public static String aprobado(BigDecimal amount, String currency) {
        return "Tu pago de %s fue aprobado. Estamos preparando tu pedido."
                .formatted(importe(amount, currency));
    }

    /** Texto de un pago rechazado. */
    public static String rechazado(BigDecimal amount, String currency) {
        return "Tu pago de %s fue rechazado. No se realizo ningun cobro."
                .formatted(importe(amount, currency));
    }

    /**
     * Formatea el importe para una persona: separador de miles y dos decimales, seguido de la
     * moneda. Se usa una configuracion regional fija para que el texto no cambie con la del
     * servidor.
     */
    private static String importe(BigDecimal amount, String currency) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.of("es", "CO")));
        return "%s %s".formatted(formato.format(amount), currency);
    }
}
