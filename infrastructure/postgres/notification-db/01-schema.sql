-- Notification DB — esquema inicial.
-- HU-002. Propietario exclusivo: notification-service (regla arquitectónica 2).
-- Contrato: docs/wiki/03-contratos/persistencia.md

BEGIN;

-- Notificaciones. Estados y transiciones: docs/wiki/02-arquitectura/comportamiento-del-flujo.md
CREATE TABLE IF NOT EXISTS notifications (
    id           UUID         PRIMARY KEY,
    order_id     UUID         NOT NULL,
    payment_id   UUID,
    channel      VARCHAR(20)  NOT NULL,
    destination  VARCHAR(255) NOT NULL,
    content      TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    failure_code VARCHAR(50),
    attempts     INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT notifications_status_valido   CHECK (status IN ('PENDIENTE', 'ENVIADA', 'FALLIDA')),
    CONSTRAINT notifications_canal_valido    CHECK (channel IN ('EMAIL')),
    CONSTRAINT notifications_attempts_no_neg CHECK (attempts >= 0)
);

COMMENT ON TABLE  notifications IS 'Notificaciones de FoodFlow. Único propietario: notification-service.';
COMMENT ON COLUMN notifications.destination IS 'Dato personal: se registra enmascarado en logs (a***@dominio.com).';
COMMENT ON COLUMN notifications.status IS 'Un fallo de negocio del proveedor deja la notificación en FALLIDA y no va a DLQ (regla 10).';
COMMENT ON COLUMN notifications.attempts IS 'Intentos contra el proveedor externo: 3 intentos, espera 500 ms que se duplica.';

-- GET /orders/{id}/notifications consulta por pedido.
CREATE INDEX IF NOT EXISTS idx_notifications_order_id ON notifications (order_id);
CREATE INDEX IF NOT EXISTS idx_notifications_status   ON notifications (status);

-- Idempotencia de consumidores (ADR-09). Se escribe en la misma transacción local que el efecto de negocio.
CREATE TABLE IF NOT EXISTS processed_events (
    event_id     UUID         PRIMARY KEY,
    consumer     VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE processed_events IS 'ADR-09: eventId único por evento procesado. El offset se confirma solo tras el commit local.';

COMMIT;
