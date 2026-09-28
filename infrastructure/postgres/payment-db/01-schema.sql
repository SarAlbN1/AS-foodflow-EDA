-- Payment DB — esquema inicial.
-- HU-002. Propietario exclusivo: payment-service (regla arquitectónica 2).
-- Contrato: docs/wiki/03-contratos/persistencia.md

BEGIN;

-- Pagos. order_id es ÚNICO: un pedido tiene a lo sumo un pago (idempotencia de negocio).
CREATE TABLE IF NOT EXISTS payments (
    id                    UUID           PRIMARY KEY,
    order_id              UUID           NOT NULL,
    amount                NUMERIC(12, 2) NOT NULL,
    status                VARCHAR(30)    NOT NULL,
    reason_code           VARCHAR(50),
    transaction_reference VARCHAR(100),
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT payments_amount_positivo CHECK (amount > 0),
    CONSTRAINT payments_status_valido   CHECK (status IN ('APROBADO', 'RECHAZADO'))
);

-- Restricción de unicidad exigida por docs/wiki/03-contratos/persistencia.md.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_order_id ON payments (order_id);

COMMENT ON TABLE  payments IS 'Pagos de FoodFlow. Único propietario: payment-service.';
COMMENT ON COLUMN payments.order_id IS 'Único: evita cobrar dos veces el mismo pedido si OrderCreated se reprocesa.';
COMMENT ON COLUMN payments.status IS 'APROBADO o RECHAZADO (HU-202). El CHECK lo impone la restricción payments_status_valido; el catálogo se documenta en docs/wiki/03-contratos/persistencia.md.';
COMMENT ON COLUMN payments.reason_code IS 'Motivo del rechazo que viaja en PaymentRejected. Único valor del prototipo: PAGO_RECHAZADO_POR_TOKEN (HU-202). Nulo cuando el pago fue aprobado.';

CREATE INDEX IF NOT EXISTS idx_payments_status     ON payments (status);
CREATE INDEX IF NOT EXISTS idx_payments_created_at ON payments (created_at);

-- Idempotencia de consumidores (ADR-09). Se escribe en la misma transacción local que el efecto de negocio.
CREATE TABLE IF NOT EXISTS processed_events (
    event_id     UUID         PRIMARY KEY,
    consumer     VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE processed_events IS 'ADR-09: eventId único por evento procesado. El offset se confirma solo tras el commit local.';

COMMIT;
