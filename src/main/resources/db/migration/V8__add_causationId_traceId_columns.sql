ALTER TABLE outbox_events
    ADD COLUMN causation_id UUID NOT NULL,
    ADD COLUMN trace_id UUID NOT NULL;

CREATE INDEX idx_outbox_events_trace_id ON outbox_events (trace_id);