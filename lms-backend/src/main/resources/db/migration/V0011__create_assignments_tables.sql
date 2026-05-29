-- ============================================================
-- V0011__create_assignments_tables.sql
--
-- assignments            — assignment definitions created by a teacher/assistant.
-- assignment_submissions — one row per student per assignment; holds the
--                          submission content and grading outcome.
--
-- Submission type controls what students may submit:
--   FILE — S3/MinIO upload only
--   TEXT — inline text / rich-text only
--   BOTH — either or both
--
-- Submission status lifecycle:
--   DRAFT ──(submit before due)──► SUBMITTED
--   DRAFT ──(submit after due)───► LATE
--   SUBMITTED / LATE ──(graded)──► GRADED
-- ============================================================

-- ── assignments ───────────────────────────────────────────────────────────────

CREATE TABLE assignments (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    psc_id                UUID           NOT NULL,
    created_by            UUID           NOT NULL,

    title                 VARCHAR(255)   NOT NULL,
    description           TEXT,                          -- instructions / rubric

    -- Controls what the student may submit
    submission_type       VARCHAR(10)    NOT NULL DEFAULT 'FILE'
                          CONSTRAINT assignments_subtype_chk
                          CHECK (submission_type IN ('FILE', 'TEXT', 'BOTH')),

    total_marks           NUMERIC(7, 2)  NOT NULL
                          CONSTRAINT assignments_marks_chk CHECK (total_marks > 0),

    due_date              TIMESTAMP      NOT NULL,

    allow_late_submission BOOLEAN        NOT NULL DEFAULT FALSE,

    -- Percentage deducted from marks for a late submission (NULL = no penalty rule)
    late_penalty_percent  NUMERIC(5, 2)
                          CONSTRAINT assignments_penalty_chk
                          CHECK (late_penalty_percent IS NULL
                              OR (late_penalty_percent >= 0 AND late_penalty_percent <= 100)),

    created_at            TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP,

    CONSTRAINT assignments_pk
        PRIMARY KEY (id),

    CONSTRAINT assignments_psc_fk
        FOREIGN KEY (psc_id) REFERENCES program_semester_courses (id)
        ON DELETE CASCADE,

    CONSTRAINT assignments_creator_fk
        FOREIGN KEY (created_by) REFERENCES users (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_assignments_psc_id  ON assignments (psc_id);
CREATE INDEX idx_assignments_due     ON assignments (psc_id, due_date);

CREATE TRIGGER trg_assignments_updated_at
    BEFORE UPDATE ON assignments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── assignment_submissions ────────────────────────────────────────────────────

CREATE TABLE assignment_submissions (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    assignment_id UUID          NOT NULL,
    student_id    UUID          NOT NULL,

    status        VARCHAR(10)   NOT NULL DEFAULT 'DRAFT'
                  CONSTRAINT asub_status_chk
                  CHECK (status IN ('DRAFT', 'SUBMITTED', 'LATE', 'GRADED')),

    -- ── Submission content (at least one must be non-null on submit) ──────────
    text_content  TEXT,                  -- for TEXT / BOTH type submissions
    file_key      VARCHAR(512),          -- S3/MinIO object key for FILE / BOTH type
    file_name     VARCHAR(255),          -- original filename shown in the UI
    file_size     BIGINT                 -- bytes
                  CONSTRAINT asub_file_size_chk CHECK (file_size IS NULL OR file_size > 0),

    -- Set when status transitions from DRAFT → SUBMITTED / LATE
    submitted_at  TIMESTAMP,

    -- ── Grading (populated when status = 'GRADED') ────────────────────────────
    marks_obtained NUMERIC(7, 2)
                   CONSTRAINT asub_marks_chk CHECK (marks_obtained IS NULL OR marks_obtained >= 0),
    feedback       TEXT,
    graded_by      UUID,                 -- teacher or assistant who graded
    graded_at      TIMESTAMP,

    created_at     TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP,

    CONSTRAINT asub_pk
        PRIMARY KEY (id),

    CONSTRAINT asub_assignment_fk
        FOREIGN KEY (assignment_id) REFERENCES assignments (id)
        ON DELETE CASCADE,

    CONSTRAINT asub_student_fk
        FOREIGN KEY (student_id) REFERENCES users (id)
        ON DELETE RESTRICT,

    CONSTRAINT asub_grader_fk
        FOREIGN KEY (graded_by) REFERENCES users (id)
        ON DELETE RESTRICT,

    -- One submission row per student per assignment
    CONSTRAINT asub_assignment_student_uq
        UNIQUE (assignment_id, student_id),

    -- submitted_at must be set iff status is not DRAFT
    CONSTRAINT asub_submitted_at_chk
        CHECK (
            (status = 'DRAFT'   AND submitted_at IS NULL) OR
            (status <> 'DRAFT'  AND submitted_at IS NOT NULL)
        ),

    -- Grading fields must all be set together
    CONSTRAINT asub_grading_consistency_chk
        CHECK (
            (marks_obtained IS NOT NULL AND graded_by IS NOT NULL AND graded_at IS NOT NULL
             AND status = 'GRADED')
            OR
            (marks_obtained IS NULL AND graded_by IS NULL AND graded_at IS NULL
             AND status <> 'GRADED')
        )
);

-- All submissions for an assignment (teacher's grading list)
CREATE INDEX idx_asub_assignment_id ON assignment_submissions (assignment_id);

-- All submissions by a student (student's task list / history)
CREATE INDEX idx_asub_student_id    ON assignment_submissions (student_id);

-- Ungraded submitted work — teacher dashboard query
CREATE INDEX idx_asub_pending_grade
    ON assignment_submissions (assignment_id)
    WHERE status IN ('SUBMITTED', 'LATE');

CREATE TRIGGER trg_asub_updated_at
    BEFORE UPDATE ON assignment_submissions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
