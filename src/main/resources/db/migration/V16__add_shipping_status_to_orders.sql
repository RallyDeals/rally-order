ALTER TABLE orders
    ADD COLUMN shipping_status VARCHAR(20)
        CHECK (shipping_status IN ('PROCESSING', 'SHIPPING', 'DELIVERED'));

ALTER TABLE orders
    ADD COLUMN shipping_status_updated_at TIMESTAMPTZ;

CREATE INDEX idx_orders_shipping_status_updated_at
    ON orders (shipping_status, shipping_status_updated_at);
