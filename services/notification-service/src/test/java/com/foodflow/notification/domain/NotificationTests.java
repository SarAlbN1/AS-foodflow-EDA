package com.foodflow.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Estado inicial de la notificacion (criterio 2 de HU-301) y la senal de entidad nueva que
 * {@code Persistable} necesita.
 */
class NotificationTests {

    @Test
    @DisplayName("nace PENDIENTE, sin intentos y sin motivo de fallo")
    void naceSinIntentos() {
        Notification notificacion = pendiente();

        assertThat(notificacion.status()).isEqualTo(NotificationStatus.PENDIENTE);
        assertThat(notificacion.attempts()).isZero();
        assertThat(notificacion.failureCode()).isNull();
        assertThat(notificacion.id()).isNotNull();
        assertThat(notificacion.createdAt()).isEqualTo(notificacion.updatedAt());
    }

    /**
     * El identificador se asigna a mano, asi que Spring Data no puede deducir que la entidad es
     * nueva: si {@code isNew()} devolviera {@code false}, {@code save} haria {@code merge} en
     * lugar de {@code persist} y cada insercion pagaria un {@code SELECT} de mas. La fila se
     * escribiria igual —el identificador es aleatorio y esa lectura nunca encuentra nada—, de
     * modo que esto guarda el coste, no la correccion.
     */
    @Test
    @DisplayName("una notificacion recien creada se declara nueva para que save haga persist")
    void laRecienCreadaEsNueva() {
        Notification notificacion = pendiente();

        assertThat(notificacion.isNew()).isTrue();
        assertThat(notificacion.getId()).isEqualTo(notificacion.id());

        notificacion.marcarComoPersistida();

        assertThat(notificacion.isNew()).isFalse();
    }

    private static Notification pendiente() {
        return Notification.pendiente(UUID.randomUUID(), UUID.randomUUID(), NotificationChannel.EMAIL,
                "ana@foodflow.test", "Tu pago de 45.900,00 COP fue aprobado.");
    }

    @Test
    @DisplayName("criterio 1 de HU-303: pasa de PENDIENTE a ENVIADA con sus intentos")
    void pasaAEnviada() {
        Notification notificacion = pendiente();

        assertThat(notificacion.marcarEnviada(2)).isTrue();

        assertThat(notificacion.status()).isEqualTo(NotificationStatus.ENVIADA);
        assertThat(notificacion.attempts()).isEqualTo(2);
        assertThat(notificacion.failureCode()).isNull();
        assertThat(notificacion.updatedAt()).isAfterOrEqualTo(notificacion.createdAt());
    }

    @Test
    @DisplayName("una segunda transicion se ignora sin error, para no publicar dos veces el hecho")
    void noVuelveAEnviarse() {
        Notification notificacion = pendiente();
        notificacion.marcarEnviada(1);

        // comportamiento-del-flujo.md: cualquier transicion que no sea PENDIENTE -> ENVIADA o
        // PENDIENTE -> FALLIDA se ignora con WARN, sin error y sin evento.
        assertThat(notificacion.marcarEnviada(5)).isFalse();

        assertThat(notificacion.attempts()).isEqualTo(1);
        assertThat(notificacion.status()).isEqualTo(NotificationStatus.ENVIADA);
    }

    @Test
    @DisplayName("un envio sin intentos no tiene sentido y se rechaza")
    void exigeAlMenosUnIntento() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> pendiente().marcarEnviada(0));
    }

    @Test
    @DisplayName("criterio 1 de HU-304: pasa a FALLIDA con su motivo y sus intentos")
    void pasaAFallida() {
        Notification notificacion = pendiente();

        assertThat(notificacion.marcarFallida("PROVEEDOR_NO_DISPONIBLE", 3)).isTrue();

        assertThat(notificacion.status()).isEqualTo(NotificationStatus.FALLIDA);
        assertThat(notificacion.failureCode()).isEqualTo("PROVEEDOR_NO_DISPONIBLE");
        assertThat(notificacion.attempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("una notificacion ya enviada no puede pasar a FALLIDA")
    void noRetrocedeDeEnviadaAFallida() {
        Notification notificacion = pendiente();
        notificacion.marcarEnviada(1);

        assertThat(notificacion.marcarFallida("PROVEEDOR_NO_DISPONIBLE", 3)).isFalse();

        assertThat(notificacion.status()).isEqualTo(NotificationStatus.ENVIADA);
        assertThat(notificacion.failureCode()).isNull();
    }

    @Test
    @DisplayName("un fallo sin motivo no se registra: el evento lo exige para diagnosticar")
    void exigeElMotivo() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> pendiente().marcarFallida("  ", 3));
    }
}
