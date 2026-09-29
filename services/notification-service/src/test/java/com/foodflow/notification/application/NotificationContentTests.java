package com.foodflow.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-301 — el texto del mensaje.
 *
 * <p>Lo que se comprueba aqui no es la redaccion, es la regla de D-6: el mensaje habla del
 * resultado del pago y no del estado del pedido, y no lleva el destino dentro.
 */
class NotificationContentTests {

    private static final BigDecimal IMPORTE = new BigDecimal("45900.00");

    @Test
    @DisplayName("el mensaje habla del pago, nunca del estado del pedido (D-6)")
    void hablaDelPagoYNoDelPedido() {
        String aprobado = NotificationContent.aprobado(IMPORTE, "COP");
        String rechazado = NotificationContent.rechazado(IMPORTE, "COP");

        assertThat(aprobado).contains("pago").contains("aprobado");
        assertThat(rechazado).contains("pago").contains("rechazado");
        // Si el texto afirmara algo del pedido podria ser falso: la notificacion puede salir
        // antes de que Order Service aplique su copia del evento, o aunque no llegue a hacerlo.
        assertThat(aprobado).doesNotContain("PAGADO").doesNotContain("pedido está");
        assertThat(rechazado).doesNotContain("PAGO_RECHAZADO").doesNotContain("pedido está");
    }

    @Test
    @DisplayName("el mensaje no incluye el destino: se expone por el API")
    void noIncluyeElDestino() {
        assertThat(NotificationContent.aprobado(IMPORTE, "COP")).doesNotContain("@");
        assertThat(NotificationContent.rechazado(IMPORTE, "COP")).doesNotContain("@");
    }

    @Test
    @DisplayName("un rechazo deja claro que no hubo cobro")
    void elRechazoNoDejaDudaDeCobro() {
        assertThat(NotificationContent.rechazado(IMPORTE, "COP")).contains("No se realizo ningun cobro");
    }

    @Test
    @DisplayName("el importe se escribe para una persona, con la moneda")
    void elImporteEsLegible() {
        assertThat(NotificationContent.aprobado(IMPORTE, "COP")).contains("45.900,00 COP");
        assertThat(NotificationContent.aprobado(new BigDecimal("8.20"), "COP")).contains("8,20 COP");
    }

    @Test
    @DisplayName("el texto no depende de la configuracion regional del servidor")
    void noDependeDeLaRegionDelServidor() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.US);
            assertThat(NotificationContent.aprobado(IMPORTE, "COP")).contains("45.900,00 COP");
        } finally {
            java.util.Locale.setDefault(original);
        }
    }
}
