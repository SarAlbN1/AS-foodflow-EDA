package com.foodflow.notificationprovider;

import java.util.Locale;

/** Comportamiento del mock según el dominio del destino. */
enum DeliveryMode {
    SUCCESS, FAIL, FLAKY, SLOW;

    static DeliveryMode forDestination(String destination) {
        String d = destination.toLowerCase(Locale.ROOT);
        if (d.endsWith("@fail.test")) {
            return FAIL;
        }
        if (d.endsWith("@flaky.test")) {
            return FLAKY;
        }
        if (d.endsWith("@slow.test")) {
            return SLOW;
        }
        return SUCCESS;
    }
}
