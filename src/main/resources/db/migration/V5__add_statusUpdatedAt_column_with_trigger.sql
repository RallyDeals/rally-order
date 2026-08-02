-- Adds a column that tracks the last time `status` changed, separate from
-- `updated_at` (which the existing trigger bumps on ANY column change, e.g.
-- setting payment_id on a CONFIRMED order without a status transition).
-- Reconciliation sweeps must key off status_updated_at, not updated_at,
-- or a non-status write silently resets the sweep clock for that order.

ALTER TABLE orders
    ADD COLUMN status_updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Backfill: for existing rows, current updated_at is our best guess at
-- when status last changed.
UPDATE orders SET status_updated_at = updated_at;

-- Fires only when status actually changes, so:
--   - inserts get status_updated_at = created_at (both default now())
--   - a plain UPDATE that only sets payment_id leaves status_updated_at untouched
--   - a guarded status transition (UPDATE ... SET status = ... WHERE status = ...) bumps it
CREATE OR REPLACE FUNCTION set_status_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        NEW.status_updated_at = now();
END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_orders_status_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION set_status_updated_at();

-- Replace the old sweep index (was on updated_at, which is no longer the
-- right column for "how long has this order been stuck in status X").
DROP INDEX IF EXISTS idx_orders_status_updated_at;

CREATE INDEX idx_orders_status_status_updated_at
    ON orders (status, status_updated_at);