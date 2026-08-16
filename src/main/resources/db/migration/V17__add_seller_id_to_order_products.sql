ALTER TABLE order_products
    ADD COLUMN seller_id UUID NULL;

CREATE INDEX idx_order_products_seller_id ON order_products (seller_id);
