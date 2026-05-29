-- ============================================================
-- V0012__create_quizzes_tables.sql
--
-- quizzes          — quiz definitions (availability window, time limit).
-- quiz_questions   — individual MCQ / MSQ questions with JSONB options.
-- quiz_submissions — one row per student per quiz; answers + auto-grade result.
--
-- ── JSONB shapes ────────────────────────────────────────────────────────────
--
-- quiz_questions.options  (array of choice objects):
--   [{"id":"a","text":"Option A"},{"id":"b","text":"Option B"},...]
--
-- quiz_questions.correct_answer (always an array; MCQ has exactly one element):
--   MCQ: ["a"]
--   MSQ: ["a","c"]
--
-- quiz_submissions.answers (map of questionId → selected option id array):
--   {
--     "<question-uuid>": ["a"],
--     "<question-uuid>": ["b","c"]
--   }
--
-- Auto-grading: on submission the service iterates questions, compares
-- answers[q.id] to q.correct_answer (order-insensitive set equality),
-- accumulates marks and writes the total to quiz_submissions.score.
-- ============================================================

-- ── quizzes ───────────────────────────────────────────────────────────────────

CREATE TABLE quizzes (
    id                UUID          NOT NULL DEFAULT gen_random_uuid(),
    psc_id            UUID          NOT NULL,
    created_by        UUID          NOT NULL,

    title             VARCHAR(255)  NOT NULL,
    description       TEXT,

    -- Time-box: NULL = unlimited time
    duration_minutes  INTEGER
                      CONSTRAINT quizzes_duration_chk CHECK (duration_minutes IS NULL OR duration_minutes > 0),

    -- Cached sum of quiz_questions.marks; updated by the service when questions change
    total_marks       NUMERIC(7, 2) NOT NULL DEFAULT 0
                      CONSTRAINT quizzes_total_marks_chk CHECK (total_marks >= 0),

    -- Availability window (NULL = not yet scheduled / always open)
    available_from    TIMESTAMP,
    available_until   TIMESTAMP,

    -- Randomisation
    shuffle_questions BOOLEAN       NOT NULL DEFAULT FALSE,
    shuffle_options   BOOLEAN       NOT NULL DEFAULT FALSE,

    created_at        TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP,

    CONSTRAINT quizzes_pk
        PRIMARY KEY (id),

    CONSTRAINT quizzes_psc_fk
        FOREIGN KEY (psc_id) REFERENCES program_semester_courses (id)
        ON DELETE CASCADE,

    CONSTRAINT quizzes_creator_fk
        FOREIGN KEY (created_by) REFERENCES users (id)
        ON DELETE RESTRICT,

    CONSTRAINT quizzes_window_chk
        CHECK (
            available_from  IS NULL OR
            available_until IS NULL OR
            available_until > available_from
        )
);

CREATE INDEX idx_quizzes_psc_id   ON quizzes (psc_id);
CREATE INDEX idx_quizzes_window   ON quizzes (psc_id, available_from, available_until);

CREATE TRIGGER trg_quizzes_updated_at
    BEFORE UPDATE ON quizzes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── quiz_questions ────────────────────────────────────────────────────────────

CREATE TABLE quiz_questions (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    quiz_id         UUID          NOT NULL,

    question_text   TEXT          NOT NULL,

    -- MCQ = exactly one correct option; MSQ = one or more correct options
    type            VARCHAR(5)    NOT NULL DEFAULT 'MCQ'
                    CONSTRAINT qquestion_type_chk CHECK (type IN ('MCQ', 'MSQ')),

    -- [{"id":"a","text":"..."},{"id":"b","text":"..."},...]
    options         JSONB         NOT NULL DEFAULT '[]',

    -- Always a JSON array; MCQ has exactly one element: ["a"]
    correct_answer  JSONB         NOT NULL DEFAULT '[]',

    marks           NUMERIC(6, 2) NOT NULL
                    CONSTRAINT qquestion_marks_chk CHECK (marks > 0),

    -- Display order within the quiz
    order_index     INTEGER       NOT NULL DEFAULT 0,

    -- Optional rationale shown to student after submission
    explanation     TEXT,

    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP,

    CONSTRAINT qquestion_pk
        PRIMARY KEY (id),

    CONSTRAINT qquestion_quiz_fk
        FOREIGN KEY (quiz_id) REFERENCES quizzes (id)
        ON DELETE CASCADE,

    CONSTRAINT qquestion_order_uq
        UNIQUE (quiz_id, order_index)
);

-- Ordered question list for rendering and grading
CREATE INDEX idx_qquestion_quiz_order ON quiz_questions (quiz_id, order_index);

CREATE TRIGGER trg_qquestion_updated_at
    BEFORE UPDATE ON quiz_questions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── quiz_submissions ──────────────────────────────────────────────────────────

CREATE TABLE quiz_submissions (
    id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    quiz_id      UUID          NOT NULL,
    student_id   UUID          NOT NULL,

    -- {"<question-uuid>":["a"],"<question-uuid>":["b","c"]}
    -- Populated incrementally while the student works; finalized on submit
    answers      JSONB         NOT NULL DEFAULT '{}',

    -- NULL while the submission is in progress (not yet submitted)
    submitted_at TIMESTAMP,

    -- Computed on submission by auto-grader; NULL until submitted
    score        NUMERIC(7, 2)
                 CONSTRAINT qsub_score_chk CHECK (score IS NULL OR score >= 0),

    -- TRUE once the auto-grader has run; always TRUE for MCQ/MSQ quizzes
    is_auto_graded BOOLEAN     NOT NULL DEFAULT FALSE,

    -- Time the student opened the quiz (to enforce duration_minutes server-side)
    started_at   TIMESTAMP     NOT NULL DEFAULT now(),

    created_at   TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP,

    CONSTRAINT qsub_pk
        PRIMARY KEY (id),

    CONSTRAINT qsub_quiz_fk
        FOREIGN KEY (quiz_id) REFERENCES quizzes (id)
        ON DELETE CASCADE,

    CONSTRAINT qsub_student_fk
        FOREIGN KEY (student_id) REFERENCES users (id)
        ON DELETE RESTRICT,

    -- One attempt per student per quiz
    CONSTRAINT qsub_quiz_student_uq
        UNIQUE (quiz_id, student_id),

    -- score is set iff the submission has been graded
    CONSTRAINT qsub_grading_consistency_chk
        CHECK (
            (is_auto_graded = TRUE  AND submitted_at IS NOT NULL AND score IS NOT NULL) OR
            (is_auto_graded = FALSE AND (score IS NULL OR submitted_at IS NOT NULL))
        ),

    -- submitted_at must be at or after started_at
    CONSTRAINT qsub_timing_chk
        CHECK (submitted_at IS NULL OR submitted_at >= started_at)
);

-- All submissions for a quiz (teacher result view)
CREATE INDEX idx_qsub_quiz_id     ON quiz_submissions (quiz_id);

-- All quiz attempts by a student (student history)
CREATE INDEX idx_qsub_student_id  ON quiz_submissions (student_id);

-- In-progress submissions (timed-out cleanup job)
CREATE INDEX idx_qsub_in_progress
    ON quiz_submissions (quiz_id, started_at)
    WHERE submitted_at IS NULL;

CREATE TRIGGER trg_qsub_updated_at
    BEFORE UPDATE ON quiz_submissions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
