-- ============================================================
-- V0010__create_attendance_tables.sql
--
-- attendance_sessions — one row per class meeting opened by a teacher/assistant.
-- attendance_records  — one row per student per session (PRESENT/ABSENT/LATE/EXCUSED).
--
-- Attendance % per student per course is computed at the application layer
-- (cached in Redis under CacheNames.ATTENDANCE_SUMMARY).  An
-- AttendanceAlertEvent is published whenever the running % drops below the
-- configured threshold (app.attendance.threshold-percentage).
-- ============================================================

-- ── attendance_sessions ───────────────────────────────────────────────────────

CREATE TABLE attendance_sessions (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    psc_id      UUID         NOT NULL,

    -- Teacher or assistant who opened the session
    created_by  UUID         NOT NULL,

    -- Calendar date of the class (allows multiple sessions on the same day)
    session_date DATE         NOT NULL,

    -- Optional note on the topic covered, shown in the teacher's record view
    topic       VARCHAR(255),

    -- When the session was opened for attendance marking
    opened_at   TIMESTAMP    NOT NULL DEFAULT now(),

    -- NULL while the session is still open; set when the teacher closes it
    closed_at   TIMESTAMP,

    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP,

    CONSTRAINT attendance_sessions_pk
        PRIMARY KEY (id),

    CONSTRAINT attendance_sessions_psc_fk
        FOREIGN KEY (psc_id) REFERENCES program_semester_courses (id)
        ON DELETE CASCADE,

    CONSTRAINT attendance_sessions_creator_fk
        FOREIGN KEY (created_by) REFERENCES users (id)
        ON DELETE RESTRICT,

    -- closed_at may only be set after opened_at
    CONSTRAINT attendance_sessions_timing_chk
        CHECK (closed_at IS NULL OR closed_at >= opened_at)
);

CREATE INDEX idx_att_sessions_psc_id   ON attendance_sessions (psc_id);
CREATE INDEX idx_att_sessions_date     ON attendance_sessions (psc_id, session_date);

-- Partial index: find the open session(s) for a course quickly
CREATE INDEX idx_att_sessions_open
    ON attendance_sessions (psc_id)
    WHERE closed_at IS NULL;

CREATE TRIGGER trg_attendance_sessions_updated_at
    BEFORE UPDATE ON attendance_sessions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── attendance_records ────────────────────────────────────────────────────────

CREATE TABLE attendance_records (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    session_id  UUID        NOT NULL,
    student_id  UUID        NOT NULL,

    -- PRESENT / ABSENT / LATE / EXCUSED
    -- PRESENT + LATE both count toward the attendance percentage.
    -- EXCUSED is excluded from both numerator and denominator.
    status      VARCHAR(10) NOT NULL
                CONSTRAINT attendance_records_status_chk
                CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED')),

    -- User who created or last updated this record (teacher, assistant, or system)
    marked_by   UUID        NOT NULL,

    remarks     TEXT,

    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP,

    CONSTRAINT attendance_records_pk
        PRIMARY KEY (id),

    CONSTRAINT attendance_records_session_fk
        FOREIGN KEY (session_id) REFERENCES attendance_sessions (id)
        ON DELETE CASCADE,

    CONSTRAINT attendance_records_student_fk
        FOREIGN KEY (student_id) REFERENCES users (id)
        ON DELETE RESTRICT,

    CONSTRAINT attendance_records_marker_fk
        FOREIGN KEY (marked_by) REFERENCES users (id)
        ON DELETE RESTRICT,

    -- One record per student per session
    CONSTRAINT attendance_records_session_student_uq
        UNIQUE (session_id, student_id)
);

-- All records for a session (bulk fetch when teacher opens the register)
CREATE INDEX idx_att_records_session_id   ON attendance_records (session_id);

-- All records for a student across courses (attendance history / summary)
CREATE INDEX idx_att_records_student_id   ON attendance_records (student_id);

-- Per-student per-session summary query:
--   SELECT status, COUNT(*) FROM attendance_records
--   WHERE student_id = :s AND session_id IN (SELECT id FROM attendance_sessions WHERE psc_id = :p)
--   GROUP BY status;
-- Covered by the two indexes above; no composite index needed.

CREATE TRIGGER trg_attendance_records_updated_at
    BEFORE UPDATE ON attendance_records
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
