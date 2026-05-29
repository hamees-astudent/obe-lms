-- ============================================================
-- V0005__create_semesters_table.sql
--
-- semesters — academic time-periods within a programme.
-- Lifecycle:  OPEN ──(admin closes)──► CLOSED
--
-- Closing a semester fires a SemesterEvent.CLOSED which triggers:
--   • transcript snapshot generation for all enrolled students
--   • CLO/PLO attainment computation
-- Once CLOSED the semester is immutable (no new grades or attendance).
-- ============================================================

CREATE TABLE semesters (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    program_id  UUID          NOT NULL,

    -- Human-readable label, e.g. "Fall 2025", "Spring 2026"
    name        VARCHAR(100)  NOT NULL,

    start_date  DATE          NOT NULL,
    end_date    DATE          NOT NULL,

    -- OPEN = accepting enrolments/grades  |  CLOSED = locked, transcripts generated
    status      VARCHAR(10)   NOT NULL DEFAULT 'OPEN'
                CONSTRAINT semesters_status_chk
                CHECK (status IN ('OPEN', 'CLOSED')),

    -- Audit trail for the closure event (NULL while status = 'OPEN')
    closed_at   TIMESTAMP,
    closed_by   UUID,           -- references users(id); SET NULL on user delete

    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP,

    CONSTRAINT semesters_pk
        PRIMARY KEY (id),

    CONSTRAINT semesters_program_fk
        FOREIGN KEY (program_id) REFERENCES programs (id)
        ON DELETE RESTRICT,     -- cannot delete a programme that has semesters

    CONSTRAINT semesters_closed_by_fk
        FOREIGN KEY (closed_by) REFERENCES users (id)
        ON DELETE SET NULL,

    -- Semester name must be unique within a programme
    CONSTRAINT semesters_program_name_uq
        UNIQUE (program_id, name),

    CONSTRAINT semesters_dates_chk
        CHECK (end_date > start_date),

    -- closed_at must be set if and only if status = 'CLOSED'
    CONSTRAINT semesters_closed_consistency_chk
        CHECK (
            (status = 'CLOSED' AND closed_at IS NOT NULL) OR
            (status = 'OPEN'   AND closed_at IS NULL)
        )
);

-- ── Indexes ───────────────────────────────────────────────────────────────────

CREATE INDEX idx_semesters_program_id ON semesters (program_id);
CREATE INDEX idx_semesters_status     ON semesters (status);

-- Partial index: enforces at most one OPEN semester per programme at any time.
-- Prevents accidentally opening a second semester while one is already active.
CREATE UNIQUE INDEX idx_semesters_one_open_per_program
    ON semesters (program_id)
    WHERE status = 'OPEN';

CREATE TRIGGER trg_semesters_updated_at
    BEFORE UPDATE ON semesters
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
