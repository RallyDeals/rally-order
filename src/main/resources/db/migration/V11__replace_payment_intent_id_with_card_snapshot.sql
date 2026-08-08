ALTER TABLE orders DROP COLUMN payment_intent_id;

ALTER TABLE orders ADD COLUMN card_last4 VARCHAR(4);
ALTER TABLE orders ADD COLUMN card_brand VARCHAR(20);
ALTER TABLE orders ADD COLUMN card_exp_month SMALLINT CHECK (card_exp_month BETWEEN 1 AND 12);
ALTER TABLE orders ADD COLUMN card_exp_year SMALLINT;
