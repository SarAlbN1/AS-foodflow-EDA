package com.foodflow.order.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import com.foodflow.order.application.OrderNotFoundException;

/**
 * Clasificacion de errores del consumidor (HU-602, corregida tras la medicion de HU-608).
 *
 * <p>{@code removeClassification} devuelve la clasificacion previa: {@code false} si la excepcion
 * estaba marcada como <strong>no reintentable</strong> y {@code null} si nunca se clasifico, que
 * es el caso por omision —reintentar— del {@code DefaultErrorHandler}.
 */
class KafkaErrorHandlerConfigTests {

    private final DefaultErrorHandler manejador = new KafkaErrorHandlerConfig().kafkaErrorHandler(
            mock(KafkaTemplate.class), 3, Duration.ofSeconds(1), 2.0);

    @Test
    @DisplayName("un evento de pago sobre un pedido que no existe no se reintenta")
    void elPedidoAusenteNoSeReintenta() {
        assertThat(manejador.removeClassification(OrderNotFoundException.class)).isFalse();
    }

    @Test
    @DisplayName("un evento fuera de contrato sigue sin reintentarse")
    void elEventoNoProcesableNoSeReintenta() {
        assertThat(manejador.removeClassification(UnsupportedEventException.class)).isFalse();
    }

    @Test
    @DisplayName("un fallo transitorio de la base conserva su rama de reintentos")
    void elFalloTransitorioSigueReintentandose() {
        // Sin clasificacion explicita: el manejador la reintenta con la espera configurada.
        assertThat(manejador.removeClassification(DataAccessResourceFailureException.class)).isNull();
    }
}
