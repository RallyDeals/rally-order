BEGIN;

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_card_exp_month_check;

ALTER TABLE orders ALTER COLUMN card_exp_month TYPE VARCHAR(2) USING card_exp_month::VARCHAR;
ALTER TABLE orders ALTER COLUMN card_exp_year TYPE VARCHAR(4) USING card_exp_year::VARCHAR;

COMMIT;