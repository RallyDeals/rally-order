-- =====================================================================
-- Manual verification script for V1__create_orders_tables.sql
-- and V2__create_events_tables.sql
--
-- Run against a local dev DB only. Several statements are EXPECTED
-- TO FAIL — that's the point, not a bug. Read each comment before
-- running, or run block by block and compare against "Expected".
--
-- Cleanup statements are included at the bottom to remove test rows.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Check 1: invalid `status` value is rejected
-- Expected: ERROR — violates check constraint "orders_status_check"
-- ---------------------------------------------------------------------
INSERT INTO orders (user_id, order_type, status, total_price)
VALUES (gen_random_uuid(), 'NORMAL', 'not_a_real_status', 10.00);


-- ---------------------------------------------------------------------
-- Check 1b: DEAL row missing participant_id is rejected
-- Expected: ERROR — violates check constraint "deal_fields_consistency"
-- ---------------------------------------------------------------------
INSERT INTO orders (user_id, order_type, status, total_price, deal_id)
VALUES (gen_random_uuid(), 'DEAL', 'pending_authorization', 10.00, gen_random_uuid());


-- ---------------------------------------------------------------------
-- Check 1c (control): valid DEAL row with both fields succeeds
-- Expected: SUCCESS — proves the constraint isn't over-blocking
-- ---------------------------------------------------------------------
INSERT INTO orders (user_id, order_type, status, total_price, deal_id, participant_id)
VALUES (gen_random_uuid(), 'DEAL', 'pending_authorization', 10.00, gen_random_uuid(), gen_random_uuid());


-- ---------------------------------------------------------------------
-- Check 2: duplicate (deal_id, participant_id) blocked for DEAL rows
-- Expected: first INSERT succeeds, second FAILS
--   ERROR — duplicate key value violates unique constraint
--   "uq_orders_deal_participant"
-- ---------------------------------------------------------------------
INSERT INTO orders (user_id, order_type, status, total_price, deal_id, participant_id)
VALUES (gen_random_uuid(), 'DEAL', 'pending_authorization', 10.00,
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222');

INSERT INTO orders (user_id, order_type, status, total_price, deal_id, participant_id)
VALUES (gen_random_uuid(), 'DEAL', 'pending_authorization', 10.00,
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222');


-- ---------------------------------------------------------------------
-- Check 2b: NORMAL rows (deal_id/participant_id both NULL) never
-- collide with each other, since the unique index is partial
-- (WHERE order_type = 'DEAL')
-- Expected: BOTH inserts succeed
-- ---------------------------------------------------------------------
INSERT INTO orders (user_id, order_type, status, total_price)
VALUES (gen_random_uuid(), 'NORMAL', 'reserving', 10.00);

INSERT INTO orders (user_id, order_type, status, total_price)
VALUES (gen_random_uuid(), 'NORMAL', 'reserving', 10.00);


-- ---------------------------------------------------------------------
-- Check 3: updated_at trigger actually fires on UPDATE
-- Expected: updated_at changes to a newer timestamp than created_at
-- ---------------------------------------------------------------------
-- Grab an id to test with (run separately, copy an id from the result):
-- SELECT id, created_at, updated_at FROM orders LIMIT 1;

-- Then, substituting the real id:
-- UPDATE orders SET status = 'confirmed' WHERE id = '<paste-id-here>';
-- SELECT id, created_at, updated_at FROM orders WHERE id = '<paste-id-here>';
-- updated_at should now be later than created_at (and later than before the UPDATE)


-- ---------------------------------------------------------------------
-- Check 4: flyway_schema_history shows both migrations applied cleanly
-- Expected: two rows, version 1 and version 2, both success = true
-- (Run this after a fresh `docker compose down -v` + app restart to
-- confirm a clean database migrates correctly from scratch.)
-- ---------------------------------------------------------------------
SELECT version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;


-- =====================================================================
-- CLEANUP — remove test rows created by this script
-- =====================================================================
DELETE FROM orders WHERE total_price = 10.00;