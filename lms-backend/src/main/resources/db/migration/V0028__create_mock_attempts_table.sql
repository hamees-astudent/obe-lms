-- ============================================================
-- V0028__create_mock_attempts_table.sql
--
-- mock_attempts — a practice quiz or paper the study assistant generated for
--                 one student from their course material.
--
-- Deliberately separate from quiz_submissions: nothing here is read by the
-- transcript, so practice can never change a grade.
-- ============================================================

CREATE TABLE mock_attempts (
    id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    student_id   UUID          NOT NULL,
    psc_id       UUID          NOT NULL,

    kind         VARCHAR(5)    NOT NULL
                 CONSTRAINT mock_kind_chk CHECK (kind IN ('QUIZ', 'PAPER')),

    -- What the student asked for: topics, counts per type, difficulty
    config       JSONB         NOT NULL DEFAULT '{}',

    -- The generated questions, answer key included (hidden until submitted)
    questions    JSONB         NOT NULL DEFAULT '[]',

    -- {"<question-id>": ["a"]} or {"<question-id>": ["written answer"]}
    answers      JSONB         NOT NULL DEFAULT '{}',

    -- Per-question score and feedback once graded
    results      JSONB         NOT NULL DEFAULT '{}',

    score        NUMERIC(7, 2),
    total_marks  NUMERIC(7, 2) NOT NULL DEFAULT 0,

    status       VARCHAR(10)   NOT NULL DEFAULT 'GENERATING'
                 CONSTRAINT mock_status_chk
                 CHECK (status IN ('GENERATING', 'READY', 'GRADED', 'FAILED')),

    error        TEXT,
    created_at   TIMESTAMP     NOT NULL DEFAULT now(),
    submitted_at TIMESTAMP,
    updated_at   TIMESTAMP,

    CONSTRAINT mock_attempts_pk PRIMARY KEY (id),

    CONSTRAINT mock_attempts_student_fk
        FOREIGN KEY (student_id) REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT mock_attempts_psc_fk
        FOREIGN KEY (psc_id) REFERENCES program_semester_courses (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_mock_attempts_student ON mock_attempts (student_id, psc_id, created_at DESC);

CREATE TRIGGER trg_mock_attempts_updated_at
    BEFORE UPDATE ON mock_attempts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
