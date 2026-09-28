package com.foodflow.order.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.foodflow.order.validation.ValidatedOrderCommand;

/**
 * Huella de la solicitud de creacion, para distinguir un reintento de un conflicto (HU-107).
 *
 * <p>Se calcula sobre los <strong>campos ya validados</strong> y no sobre los bytes crudos del
 * cuerpo. Asi, dos envios identicos que solo difieran en espacios, saltos de linea o el orden
 * de las claves JSON cuentan como el mismo intento, que es lo que espera quien reintenta. Si se
 * hiciera sobre el texto recibido, un cliente que reformatease su JSON recibiria un {@code 409}
 * sin haber cambiado ningun dato.
 *
 * <p>El importe se normaliza a escala 2 antes de entrar en la huella, porque {@code 45000} y
 * {@code 45000.00} son el mismo pedido.
 */
public final class RequestHash {

    /** Separador que no puede aparecer dentro de un campo, para que la huella no sea ambigua. */
    private static final char SEPARADOR = '\u001f';

    private RequestHash() {
    }

    /** SHA-256 en hexadecimal (64 caracteres, la longitud de la columna). */
    public static String de(ValidatedOrderCommand comando) {
        String canonico = String.join(String.valueOf(SEPARADOR),
                comando.customerReference(),
                comando.customerContact(),
                comando.notificationChannel().name(),
                comando.total().stripTrailingZeros().toPlainString(),
                comando.paymentToken().valor());
        return sha256(canonico);
    }

    private static String sha256(String texto) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda implementacion de Java: si falta, el entorno esta roto.
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
