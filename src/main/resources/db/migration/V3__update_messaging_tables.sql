CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- outbox_events
-- ============================================================================

ALTER TABLE outbox_events
    ADD COLUMN aggregate_type VARCHAR(50),
    ADD COLUMN topic          VARCHAR(100),
    ADD COLUMN status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN attempts       INT NOT NULL DEFAULT 0,
    ADD COLUMN last_error     TEXT;


ALTER TABLE outbox_events
ALTER COLUMN aggregate_id TYPE VARCHAR(100) USING aggregate_id::text;

ALTER TABLE outbox_events
ALTER COLUMN event_type TYPE VARCHAR(100);

CREATE INDEX idx_outbox_events_pending ON outbox_events (created_at) WHERE status = 'PENDING';

-- ============================================================================
-- processed_events
-- ============================================================================

ALTER TABLE processed_events
DROP CONSTRAINT processed_events_pkey;

-- event_id was UUID, target is VARCHAR(100)
ALTER TABLE processed_events
ALTER COLUMN event_id TYPE VARCHAR(100) USING event_id::text;

ALTER TABLE processed_events
ALTER COLUMN event_type TYPE VARCHAR(100);

ALTER TABLE processed_events
    ADD COLUMN source_topic VARCHAR(100);

ALTER TABLE processed_events
    ADD PRIMARY KEY (event_id, source_topic);