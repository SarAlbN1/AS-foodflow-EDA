package com.foodflow.order.infrastructure.messaging;

import java.time.Duration;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Politica de reintentos y DLQ del consumidor (HU-602).
 *
 * <p><strong>Que reemplaza.</strong> Sin este manejador actua el {@code DefaultErrorHandler} por
 * omision de Spring Kafka, con su {@code FixedBackOff(0, 9)}: <strong>diez intentos seguidos sin
 * espera</strong> y, agotados, confirma el offset y sigue. Un corte de unos segundos en la base
 * bastaba para perder el evento sin dejar rastro en ninguna parte.
 *
 * <p><strong>Errores transitorios:</strong> 3 intentos con espera inicial de 1 s que se duplica
 * ({@code convenciones.md}), configurables por variable de entorno.
 *
 * <p><strong>Eventos no procesables:</strong> {@link UnsupportedEventException} va a la DLQ
 * <strong>sin reintentar</strong>. Un envelope ilegible o una version no soportada no mejoran por
 * repetirlos, y reintentarlos solo retrasa al resto de la particion.
 *
 * <p><strong>Lo que NO va a la DLQ.</strong> Un fallo de negocio no es un error y no lanza, asi
 * que no llega hasta aqui. El caso claro es el del proveedor de notificaciones: deja la
 * notificacion en {@code FALLIDA} y el offset se confirma (regla 10).
 *
 * <p>Vive en {@code infrastructure.messaging} porque usa {@code KafkaTemplate}, y ningun otro
 * paquete puede usarlo ({@code ArchitectureTest}).
 */
@Configuration
public class KafkaErrorHandlerConfig {

    /** Sufijo de la DLQ de cada topico, segun la convencion del contrato de eventos. */
    static final String SUFIJO_DLQ = ".dlq";

    @Bean
    DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafka,
            @Value("${foodflow.kafka.retry.max-attempts}") int intentos,
            @Value("${foodflow.kafka.retry.initial-interval}") Duration esperaInicial,
            @Value("${foodflow.kafka.retry.multiplier}") double multiplicador) {

        // El evento agotado se publica en <topico>.dlq conservando su particion, asi que los
        // eventos de un pedido siguen juntos tambien alli.
        DeadLetterPublishingRecoverer dlq = new DeadLetterPublishingRecoverer(kafka,
                (registro, fallo) -> new TopicPartition(registro.topic() + SUFIJO_DLQ, registro.partition()));

        ExponentialBackOff espera = new ExponentialBackOff(esperaInicial.toMillis(), multiplicador);
        // El primer envio no es un reintento: 3 intentos son el original mas 2.
        espera.setMaxAttempts(Math.max(intentos - 1, 0));

        DefaultErrorHandler manejador = new DefaultErrorHandler(dlq, espera);
        // Sin reintento: un evento fuera de contrato no cambia por repetirlo.
        manejador.addNotRetryableExceptions(UnsupportedEventException.class);
        return manejador;
    }
}
