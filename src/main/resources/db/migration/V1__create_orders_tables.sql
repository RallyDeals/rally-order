CREATE TABLE orders (
                        id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        user_id         UUID NOT NULL,
                        order_type      VARCHAR(10) NOT NULL CHECK (order_type IN ('NORMAL', 'DEAL')),

                        deal_id         UUID NULL,               -- required if order_type = 'DEAL'
                        participant_id  UUID NULL,               -- required if order_type = 'DEAL'

                        status          VARCHAR(50) NOT NULL CHECK (status IN (
                                                                               'reserving',             -- NORMAL: inventory reservation in flight
                                                                               'pending_charge',        -- NORMAL: waiting on CHARGE outcome
                                                                               'pending_authorization',  -- DEAL: waiting on AUTHORIZE outcome
                                                                               'authorized',            -- DEAL: hold placed, waiting on deal resolution
                                                                               'pending_capture',       -- DEAL: deal succeeded, waiting on CAPTURE outcome
                                                                               'pending_void',          -- DEAL: deal failed, waiting on VOID outcome
                                                                               'confirmed',             -- terminal
                                                                               'cancelled'              -- terminal
                            )),

                        total_price     NUMERIC(10,2) NOT NULL CHECK (total_price >= 0),
                        payment_id      UUID NULL,               -- set once Payment Service returns a payment_id
                        payment_intent_id VARCHAR(255) NULL,
                        cancel_reason   VARCHAR(30) NULL CHECK (cancel_reason IN (
                        'insufficient_stock', 'inventory_unreachable', 'reservation_incomplete',
                        'payment_declined', 'payment_timeout', 'deal_failed', 'participant_left'
                     )),

                        version         INT NOT NULL DEFAULT 0,  -- optimistic locking / race auditing
                        created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

                        CONSTRAINT deal_fields_consistency CHECK (
                            (order_type = 'DEAL'   AND deal_id IS NOT NULL AND participant_id IS NOT NULL) OR
                            (order_type = 'NORMAL' AND deal_id IS NULL AND participant_id IS NULL)
                            )
);

-- Guards against a redelivered participant.joined creating two orders for the same slot.
CREATE UNIQUE INDEX uq_orders_deal_participant
    ON orders (deal_id, participant_id)
    WHERE order_type = 'DEAL';

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_deal_id ON orders (deal_id);
CREATE INDEX idx_orders_status  ON orders (status);
-- Used by every reconciliation sweep (reserving / pending_charge / pending_authorization / pending_capture / pending_void)
CREATE INDEX idx_orders_status_updated_at ON orders (status, updated_at);

-- Keeps updated_at accurate automatically so sweep jobs never rely on
-- application code remembering to set it manually on every status change.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE order_products (
                                id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                order_id    UUID NOT NULL REFERENCES orders(id),
                                product_id  UUID NOT NULL,
                                quantity    INT NOT NULL CHECK (quantity > 0),   -- always 1 for DEAL, can be >1 for NORMAL
                                unit_price  NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0),
                                created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_products_order_id ON order_products (order_id);