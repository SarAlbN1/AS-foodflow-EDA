package com.foodflow.notification.application;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.foodflow.notification.domain.Notification;

/**
 * Une los dos pasos de una notificacion: registrarla y entregarla.
 *
 * <p>Es el punto por el que entra {@code infrastructure.messaging}: el consumidor le pasa la
 * orden ya validada y no conoce ni la transaccion ni el proveedor.
 *
 * <p><strong>Por que es un componente aparte y no un metodo mas de
 * {@link NotificationApplicationService}.</strong> El envio al proveedor <strong>no puede ocurrir
 * dentro de la transaccion</strong> que crea la notificacion: con 3 intentos y 3 s de lectura,
 * mantendria la conexion a Notification DB tomada varios segundos por notificacion. Y llamar a un
 * metodo {@code @Transactional} desde otro metodo de la misma clase no pasa por el proxy de
 * Spring, asi que la transaccion no existiria. Separarlos es lo que garantiza las dos cosas: el
 * registro va en su transaccion y el envio queda fuera.
 *
 * <p>Un envio aceptado deja la notificacion en {@code ENVIADA} y publica
 * {@code NotificationSent} (HU-303). El caso fallido todavia no se persiste: {@code FALLIDA} y
 * {@code NotificationFailed} los escribe HU-304.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationApplicationService notificaciones;
    private final NotificationSender proveedor;

    public NotificationDispatcher(NotificationApplicationService notificaciones,
            NotificationSender proveedor) {
        this.notificaciones = notificaciones;
        this.proveedor = proveedor;
    }

    /**
     * Registra la notificacion del resultado del pago y la entrega al proveedor.
     *
     * <p>Si el evento ya estaba procesado (ADR-09) no hay nada que entregar: devolver vacio es lo
     * que evita que una reentrega de Kafka produzca un segundo envio al proveedor, que es el
     * efecto visible para el cliente.
     *
     * @return el resultado del envio, o vacio si el evento ya se habia procesado
     */
    public Optional<DeliveryOutcome> procesar(NotifyPaymentResultCommand orden) {
        Optional<Notification> creada = notificaciones.notificarResultado(orden);
        if (creada.isEmpty()) {
            return Optional.empty();
        }

        Notification notificacion = creada.get();
        DeliveryOutcome resultado = proveedor.enviar(notificacion, String.valueOf(orden.correlationId()));

        if (resultado.aceptado()) {
            // Fuera del envio y en su propia transaccion (HU-303). Va aqui y no dentro de
            // notificarResultado porque el commit de la notificacion no puede esperar a que el
            // proveedor conteste.
            notificaciones.registrarEnvio(notificacion.id(), resultado.providerReference(),
                    resultado.attempts(), orden.correlationId());
        } else {
            // No se relanza: un fallo del proveedor es resultado de negocio (regla 10) y no debe
            // impedir que el offset se confirme ni que Order Service registre el pago (regla 12).
            log.warn("El proveedor no acepto la notificacion notificationId={} orderId={} fallo={} "
                            + "intentos={} correlationId={}",
                    notificacion.id(), notificacion.orderId(), resultado.failure(),
                    resultado.attempts(), orden.correlationId());
        }
        return Optional.of(resultado);
    }
}
