package com.foodflow.notificationprovider;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/** Cuenta los intentos por destino {@code *@flaky.test}. Vive en memoria: se reinicia con el contenedor. */
@Component
class FlakyAttempts {

    private final ConcurrentMap<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

    /** Registra un intento y devuelve su número (1, 2, 3...). */
    int register(String destination) {
        return attempts
                .computeIfAbsent(destination.toLowerCase(Locale.ROOT), key -> new AtomicInteger())
                .incrementAndGet();
    }
}
