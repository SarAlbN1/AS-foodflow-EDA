package com.foodflow.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
}
