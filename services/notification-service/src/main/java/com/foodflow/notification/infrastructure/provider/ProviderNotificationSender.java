package com.foodflow.notification.infrastructure.provider;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.foodflow.notification.application.ContactMasker;
import com.foodflow.notification.application.DeliveryFailure;
import com.foodflow.notification.application.DeliveryOutcome;
import com.foodflow.notification.application.NotificationSender;
import com.foodflow.notification.domain.Notification;

/**
 * Adaptador HTTP del proveedor externo (HU-302). <strong>Unico punto del sistema con un cliente
 * HTTP saliente</strong> (regla arquitectonica 7): ni Order ni Payment lo tienen, y ningun otro
 * paquete de este servicio puede tenerlo ({@code ArchitectureTest}).
 *
 * <p>Contrato: {@code POST /v1/messages} con {@code {channel, destination, content,
 * correlationId}} y {@code 202} con {@code providerReference}
 * ({@code docs/wiki/03-contratos/proveedor-notificaciones.md}).
 *
 * <p><strong>Timeouts explicitos</strong> (criterio 3): 2 s de conexion y 3 s de lectura. Sin
 * ellos el cliente esperaria indefinidamente y bloquearia el hilo del consumidor de Kafka, que
 * es el que trae el evento: una notificacion lenta detendria el consumo de todo el resto.
 *
 * <p><strong>Reintentos finitos</strong> (criterio 6): 3 intentos con espera inicial de 500 ms
 * que se duplica. <strong>Sin Circuit Breaker</strong>, que la lista de no implementar excluye
 * expresamente: con un unico proveedor no hay riesgo de fallo en cascada que lo justifique.
 */
@Component
class ProviderNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(ProviderNotificationSender.class);

    private static final String RUTA = "/v1/messages";

    /**
     * El informe tecnico fija que se envia el {@code notificationId} como clave de idempotencia,
     * para que un reintento no produzca un segundo envio con la misma clave. El proveedor
     * simulado de HU-306 todavia no la lee; se envia igual porque un proveedor real la usaria y
     * porque es lo que describe el informe.
     */
    private static final String CLAVE_DE_IDEMPOTENCIA = "Idempotency-Key";

    private final RestClient cliente;
    private final int intentosMaximos;
    private final Duration esperaInicial;
    private final int multiplicador;

    ProviderNotificationSender(
            @Value("${foodflow.provider.url}") String url,
            @Value("${foodflow.provider.connect-timeout}") Duration conexion,
            @Value("${foodflow.provider.read-timeout}") Duration lectura,
            @Value("${foodflow.provider.max-attempts}") int intentosMaximos,
            @Value("${foodflow.provider.initial-backoff}") Duration esperaInicial,
            @Value("${foodflow.provider.backoff-multiplier}") int multiplicador) {
        if (intentosMaximos < 1) {
            throw new IllegalArgumentException("foodflow.provider.max-attempts debe ser al menos 1");
        }
        this.intentosMaximos = intentosMaximos;
        this.esperaInicial = esperaInicial;
        this.multiplicador = multiplicador;
        this.cliente = RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabricaCon(conexion, lectura))
                .build();
        log.info("Proveedor de notificaciones configurado url={} conexion={} lectura={} intentos={} "
                        + "esperaInicial={} multiplicador={}",
                url, conexion, lectura, intentosMaximos, esperaInicial, multiplicador);
    }

    @Override
    public DeliveryOutcome enviar(Notification notificacion, String correlationId) {
        String destinoEnmascarado = ContactMasker.mask(notificacion.destination());
        ProviderMessage mensaje = new ProviderMessage(notificacion.channel().name(),
                notificacion.destination(), notificacion.content(), correlationId);

        DeliveryFailure ultimoFallo = null;
        int realizados = 0;
        for (int intento = 1; intento <= intentosMaximos; intento++) {
            realizados = intento;
            try {
                ProviderAcceptance aceptacion = cliente.post()
                        .uri(RUTA)
                        .header(CLAVE_DE_IDEMPOTENCIA, notificacion.id().toString())
                        .body(mensaje)
                        .retrieve()
                        .body(ProviderAcceptance.class);
                String referencia = aceptacion == null ? "" : aceptacion.providerReference();
                log.info("Notificacion entregada al proveedor notificationId={} intento={}/{} "
                                + "providerReference={} destino={} correlationId={}",
                        notificacion.id(), intento, intentosMaximos, referencia,
                        destinoEnmascarado, correlationId);
                return DeliveryOutcome.aceptado(referencia, intento);
            } catch (HttpClientErrorException rechazo) {
                // 4xx: el mensaje no es aceptable. Repetirlo produce el mismo rechazo.
                log.warn("El proveedor rechazo la notificacion notificationId={} estado={} destino={} "
                                + "correlationId={}",
                        notificacion.id(), rechazo.getStatusCode(), destinoEnmascarado, correlationId);
                return DeliveryOutcome.fallido(DeliveryFailure.PROVEEDOR_RECHAZO_EL_MENSAJE, intento);
            } catch (HttpServerErrorException caido) {
                ultimoFallo = DeliveryFailure.PROVEEDOR_NO_DISPONIBLE;
                registrarIntentoFallido(notificacion, intento, ultimoFallo,
                        String.valueOf(caido.getStatusCode()), destinoEnmascarado, correlationId);
            } catch (ResourceAccessException inalcanzable) {
                ultimoFallo = clasificar(inalcanzable);
                registrarIntentoFallido(notificacion, intento, ultimoFallo,
                        inalcanzable.getMessage(), destinoEnmascarado, correlationId);
            }

            if (intento < intentosMaximos && !esperar(espera(intento))) {
                break;
            }
        }

        log.warn("Envio agotado notificationId={} intentos={} fallo={} destino={} correlationId={}",
                notificacion.id(), realizados, ultimoFallo, destinoEnmascarado, correlationId);
        return DeliveryOutcome.fallido(ultimoFallo, realizados);
    }

    /**
     * Espera de un intento: la inicial multiplicada una vez por cada intento ya consumido, es
     * decir 500 ms y 1 s con la configuracion por omision.
     */
    private Duration espera(int intento) {
        return esperaInicial.multipliedBy((long) Math.pow(multiplicador, intento - 1));
    }

    /**
     * Un tiempo agotado y un proveedor inalcanzable llegan los dos como
     * {@link ResourceAccessException}; se distinguen por la causa para que el {@code failureCode}
     * diga cual de los dos fue.
     */
    private static DeliveryFailure clasificar(ResourceAccessException excepcion) {
        for (Throwable causa = excepcion.getCause(); causa != null; causa = causa.getCause()) {
            if (causa instanceof HttpTimeoutException || causa instanceof SocketTimeoutException) {
                return DeliveryFailure.TIEMPO_DE_ESPERA_AGOTADO;
            }
        }
        return DeliveryFailure.ERROR_DE_CONEXION;
    }

    /** @return {@code false} si la espera se interrumpio, para no seguir intentando */
    private static boolean esperar(Duration espera) {
        try {
            Thread.sleep(espera);
            return true;
        } catch (InterruptedException interrumpida) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void registrarIntentoFallido(Notification notificacion, int intento, DeliveryFailure fallo,
            String detalle, String destinoEnmascarado, String correlationId) {
        log.warn("Intento {}/{} fallido notificationId={} fallo={} detalle={} destino={} correlationId={}",
                intento, intentosMaximos, notificacion.id(), fallo, detalle, destinoEnmascarado,
                correlationId);
    }

    /** El cliente del JDK permite fijar los dos tiempos por separado, que es lo que exige el CA3. */
    private static JdkClientHttpRequestFactory fabricaCon(Duration conexion, Duration lectura) {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(conexion).build());
        fabrica.setReadTimeout(lectura);
        return fabrica;
    }
}
