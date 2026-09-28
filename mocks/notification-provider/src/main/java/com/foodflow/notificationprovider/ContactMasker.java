package com.foodflow.notificationprovider;

/** Enmascara el contacto para los logs: {@code ana@dominio.com} -> {@code a***@dominio.com}. */
final class ContactMasker {

    private ContactMasker() {
    }

    static String mask(String destination) {
        int at = destination.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return destination.charAt(0) + "***" + destination.substring(at);
    }
}
