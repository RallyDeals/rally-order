-- aggregate_id was converted to VARCHAR(100) in V3, but every aggregate this
-- service publishes events for (orders.id) is a UUID. Restore the real type.
ALTER TABLE outbox_events
ALTER COLUMN aggregate_id TYPE UUID USING aggregate_id::uuid;
