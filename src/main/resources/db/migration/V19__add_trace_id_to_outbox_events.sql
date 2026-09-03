-- trace_id holds a W3C/OTel trace id (32 lowercase hex chars), not a UUID, unlike the
-- trace_id column V18 dropped - so this is a fresh column, not a resurrection of the old one.
ALTER TABLE outbox_events
    ADD COLUMN trace_id VARCHAR(32);

CREATE INDEX idx_outbox_events_trace_id ON outbox_events (trace_id);
