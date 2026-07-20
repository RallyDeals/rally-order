-- Inbound Kafka dedup: every consumer checks/inserts here in the same transaction
-- as its business write, so at-least-once redelivery is a no-op.
CREATE TABLE processed_events (
                                  event_id      UUID PRIMARY KEY,
                                  event_type    VARCHAR(50) NOT NULL,
                                  processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Transactional outbox for every event Order Service publishes (§3). Makes the
-- publish part of the same commit as the status write; a separate relay process
-- polls WHERE published_at IS NULL and delivers to Kafka at-least-once.
CREATE TABLE outbox_events (
                               id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               aggregate_id  UUID NOT NULL,
                               event_type    VARCHAR(50) NOT NULL,
                               payload       JSONB NOT NULL,
                               created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
                               published_at  TIMESTAMPTZ NULL
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;