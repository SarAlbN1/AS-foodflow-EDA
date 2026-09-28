package com.foodflow.order.application;

/**
 * Enmascara el contacto del cliente para los registros: {@code ana@foodflow.test} se escribe
 * {@code a***@foodflow.test}. El contacto es dato personal y no se registra completo
 * ({@code docs/wiki/04-implementacion/convenciones.md}).
 */
public final class ContactMasker {

    private ContactMasker() {
    }

    public static String mask(String contacto) {
        if (contacto == null || contacto.isBlank()) {
            return "(sin contacto)";
        }
        int arroba = contacto.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        return contacto.charAt(0) + "***" + contacto.substring(arroba);
    }
}
