package com.foodflow.notification.application;

import java.util.Optional;

/**
 * Resultado de intentar entregar una notificacion al proveedor externo.
 *
 * <p>Es un <strong>resultado, no una excepcion</strong>: el criterio 4 de HU-302 exige que un
 * error HTTP o de conexion se traduzca en algo controlado y no provoque la perdida silenciosa
 * del mensaje. Quien llama decide que hacer con el, y siempre hay algo que decidir.
 *
 * <p>Quien lo persiste son HU-303 ({@code ENVIADA}) y HU-304 ({@code FALLIDA}); esta historia
 * solo lo produce y lo registra.
 *
 * @param providerReference referencia del proveedor cuando acepto el mensaje; nula si fallo
 * @param failure           motivo del fallo; nulo si el proveedor acepto
 * @param attempts          intentos realizados, siempre al menos 1
 */
public record DeliveryOutcome(String providerReference, DeliveryFailure failure, int attempts) {

    public DeliveryOutcome {
        if (attempts < 1) {
            throw new IllegalArgumentException("un envio tiene al menos un intento");
        }
        if ((providerReference == null) == (failure == null)) {
            throw new IllegalArgumentException("un envio o fue aceptado o fallo, nunca ambos ni ninguno");
        }
    }

    /** El proveedor acepto el mensaje. */
    public static DeliveryOutcome aceptado(String providerReference, int attempts) {
        return new DeliveryOutcome(providerReference, null, attempts);
    }

    /** El envio fallo por el motivo indicado, tras los intentos indicados. */
    public static DeliveryOutcome fallido(DeliveryFailure failure, int attempts) {
        return new DeliveryOutcome(null, failure, attempts);
    }

    public boolean aceptado() {
        return providerReference != null;
    }

    /** Referencia del proveedor, vacia cuando el envio fallo. */
    public Optional<String> referencia() {
        return Optional.ofNullable(providerReference);
    }
}
