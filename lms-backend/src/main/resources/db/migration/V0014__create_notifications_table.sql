-- ============================================================
-- V0014__create_notifications_table.sql
--
-- notifications — persisted in-app notifications consumed from Kafka events.
--
-- Delivery flow:
--   Kafka event → KafkaConsumer (notifications module)
--             → persist row here
--             → email via JavaMailSender (async)
--             → delivered to client via REST polling (GET /api/notifications)
--               or future WebSocket push
--
-- Supported event_type values (mirrors Kafka topic events):
--   ENROLLMENT_CONFIRMED, ENROLLMENT_DROPPED,
--   ATTENDANCE_ALERT,
--   ASSIGNMENT_SUBMITTED, ASSIGNMENT_GRADED, QUIZ_SUBMITTED,
--   SEMESTER_CLOSED, SEMESTER_REOPENED
-- ============================================================

CREATE TABLE notifications (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    recipient_id   UUID         NOT NULL,

    -- Human-readable label for the notification card
    title          VARCHAR(255) NOT NULL,
    body           TEXT,

    -- Kafka event type that generated this notification (e.g. ASSIGNMENT_GRADED)
    event_type     VARCHAR(50)  NOT NULL,

    -- Optional pointer to the relevant domain object (assignment, quiz, enrollment …)
    -- No FK — reference_id is polymorphic; integrity enforced at the application layer
    reference_type VARCHAR(30),  -- e.g. 'ASSIGNMENT', 'QUIZ', 'ENROLLMENT'
    reference_id   UUID,

    is_read        BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at        TIMESTAMP,

    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP,

    CONSTRAINT notifications_pk
        PRIMARY KEY (id),

    CONSTRAINT notifications_recipient_fk
        FOREIGN KEY (recipient_id) REFERENCES users (id)
        ON DELETE CASCADE,

    -- read_at must be set iff the notification has been read
    CONSTRAINT notifications_read_consistency_chk
        CHECK (
            (is_read = TRUE  AND read_at IS NOT NULL) OR
            (is_read = FALSE AND read_at IS NULL)
        )
);

-- Primary query: fetch a user's notification feed, newest first
CREATE INDEX idx_notifications_recipient
    ON notifications (recipient_id, created_at DESC);

-- Unread badge count query: COUNT(*) WHERE recipient_id = :id AND is_read = FALSE
CREATE INDEX idx_notifications_unread
    ON notifications (recipient_id)
    WHERE is_read = FALSE;

-- Mark-all-read query pattern: WHERE recipient_id = :id AND event_type = :t
CREATE INDEX idx_notifications_event_type
    ON notifications (recipient_id, event_type);

CREATE TRIGGER trg_notifications_updated_at
    BEFORE UPDATE ON notifications
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
