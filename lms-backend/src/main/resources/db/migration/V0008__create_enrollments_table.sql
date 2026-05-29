-- ============================================================
-- V0008__create_enrollments_table.sql
--
-- enrollments — one row per student per program_semester_course (PSC).
-- A student enrolls in individual course offerings within a semester,
-- not in the semester itself.
--
-- Status lifecycle:
--   ACTIVE ──(drop)──► DROPPED
--   ACTIVE ──(semester closed)──► COMPLETED
-- ============================================================

CREATE TABLE enrollments (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    psc_id      UUID         NOT NULL,   -- the course offering
    student_id  UUID         NOT NULL,   -- user with STUDENT role

    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                CONSTRAINT enrollments_status_chk
                CHECK (status IN ('ACTIVE', 'DROPPED', 'COMPLETED')),

    -- Populated when status transitions to DROPPED
    dropped_at  TIMESTAMP,

    -- enrolled_at defaults to now() but can be set explicitly for migrations
    enrolled_at TIMESTAMP    NOT NULL DEFAULT now(),

    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP,

    CONSTRAINT enrollments_pk
        PRIMARY KEY (id),

    CONSTRAINT enrollments_psc_fk
        FOREIGN KEY (psc_id) REFERENCES program_semester_courses (id)
        ON DELETE RESTRICT,

    CONSTRAINT enrollments_student_fk
        FOREIGN KEY (student_id) REFERENCES users (id)
        ON DELETE RESTRICT,

    -- One enrollment row per student per course offering (re-enrolment updates status)
    CONSTRAINT enrollments_psc_student_uq
        UNIQUE (psc_id, student_id),

    -- dropped_at must be set iff status = 'DROPPED'
    CONSTRAINT enrollments_dropped_consistency_chk
        CHECK (
            (status = 'DROPPED'  AND dropped_at IS NOT NULL) OR
            (status <> 'DROPPED' AND dropped_at IS NULL)
        )
);

-- ── Indexes ───────────────────────────────────────────────────────────────────

-- All enrollments for a given course offering (teacher's roster)
CREATE INDEX idx_enrollments_psc_id     ON enrollments (psc_id);

-- All courses a student is enrolled in
CREATE INDEX idx_enrollments_student_id ON enrollments (student_id);

-- Active enrollments per PSC — used for attendance, assessment, and capacity checks
CREATE INDEX idx_enrollments_psc_active
    ON enrollments (psc_id)
    WHERE status = 'ACTIVE';

-- Active enrollments per student — used for student dashboard queries
CREATE INDEX idx_enrollments_student_active
    ON enrollments (student_id)
    WHERE status = 'ACTIVE';

CREATE TRIGGER trg_enrollments_updated_at
    BEFORE UPDATE ON enrollments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
