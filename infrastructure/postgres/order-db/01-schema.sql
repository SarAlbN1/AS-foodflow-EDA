-- Order DB — esquema inicial.
-- HU-002. Propietario exclusivo: order-service (regla arquitectónica 2).
-- Contrato: docs/wiki/03-contratos/persistencia.md
-- Este script lo ejecuta el entrypoint de PostgreSQL la primera vez que se crea el volumen.

BEGIN;

-- Pedidos. Estados permitidos y transiciones: docs/wiki/02-arquitectura/comportamiento-del-flujo.md
CREATE TABLE IF NOT EXISTS orders (
    id                    UUID           PRIMARY KEY,
    customer_reference    VARCHAR(60)    NOT NULL,
    notification_channel  VARCHAR(20)    NOT NULL,
    customer_contact      VARCHAR(255)   NOT NULL,
    payment_token         VARCHAR(20)    NOT NULL,
    total                 NUMERIC(12, 2) NOT NULL,
    status                VARCHAR(20)    NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT orders_total_positivo      CHECK (total > 0),
    CONSTRAINT orders_status_valido       CHECK (status IN ('CREADO', 'PAGADO', 'PAGO_RECHAZADO')),
    CONSTRAINT orders_canal_valido        CHECK (notification_channel IN ('EMAIL')),
    CONSTRAINT orders_payment_token_valido CHECK (payment_token IN ('PAY-OK', 'PAY-FAIL'))
);

COMMENT ON TABLE  orders IS 'Pedidos de FoodFlow. Único propietario: order-service.';
COMMENT ON COLUMN orders.notification_channel IS 'Snapshot del canal de notificación (ADR-11).';
COMMENT ON COLUMN orders.customer_contact IS 'Snapshot del destino de notificación (ADR-11). Dato personal: se registra enmascarado en logs.';
COMMENT ON COLUMN orders.payment_token IS 'Pago determinista (ADR-10): PAY-OK o PAY-FAIL.';

CREATE INDEX IF NOT EXISTS idx_orders_status     ON orders (status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders (created_at);

-- Idempotencia del endpoint POST /orders (encabezado Idempotency-Key).
CREATE TABLE IF NOT EXISTS idempotency_keys (
    key          VARCHAR(200) PRIMARY KEY,
    request_hash VARCHAR(64)  NOT NULL,
    order_id     UUID         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_idempotency_keys_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

COMMENT ON TABLE  idempotency_keys IS 'Misma clave y mismo cuerpo devuelve la respuesta original; cuerpo distinto produce 409.';
COMMENT ON COLUMN idempotency_keys.request_hash IS 'Hash del cuerpo de la solicitud, para distinguir reintento de conflicto.';

-- Idempotencia de consumidores (ADR-09). Se escribe en la misma transacción local que el efecto de negocio.
CREATE TABLE IF NOT EXISTS processed_events (
    event_id     UUID         PRIMARY KEY,
    consumer     VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE processed_events IS 'ADR-09: eventId único por evento procesado. El offset se confirma solo tras el commit local.';

COMMIT;
