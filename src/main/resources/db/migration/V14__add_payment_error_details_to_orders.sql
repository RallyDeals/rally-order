ALTER TABLE orders ADD COLUMN payment_error_code VARCHAR(50);
ALTER TABLE orders ADD COLUMN payment_error_message TEXT;