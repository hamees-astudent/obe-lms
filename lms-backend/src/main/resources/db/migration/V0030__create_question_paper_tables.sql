-- ============================================================
-- V0030__create_question_paper_tables.sql
--
-- offering_paper_settings — per offering: the most marks, questions and
--                           minutes a paper may have (so no generated paper
--                           runs too long), the cap on student mock papers,
--                           and the marks table used to price questions by
--                           type × Bloom level × difficulty.
-- question_papers         — a printable paper assembled by the teacher.
-- question_paper_items    — the paper's questions. Each is a snapshot, so a
--                           printed paper never changes when its source quiz
--                           or draft is edited later.
-- paper_print_sets        — one row per printed set (A, B, C…): the random
--                           question and option order it was printed with,
--                           kept so its answer key can be reproduced.
-- ============================================================

CREATE TABLE offering_paper_settings (
    psc_id               UUID          NOT NULL,

    max_total_marks      NUMERIC(7, 2) NOT NULL DEFAULT 100
                         CONSTRAINT ops_max_marks_chk CHECK (max_total_marks > 0),
    max_questions        INTEGER       NOT NULL DEFAULT 40
                         CONSTRAINT ops_max_questions_chk CHECK (max_questions > 0),
    max_duration_minutes INTEGER       NOT NULL DEFAULT 180
                         CONSTRAINT ops_max_duration_chk CHECK (max_duration_minutes > 0),
    mock_max_total_marks NUMERIC(7, 2) NOT NULL DEFAULT 50
                         CONSTRAINT ops_mock_marks_chk CHECK (mock_max_total_marks > 0),

    -- {"MCQ":{"base":1}, "SHORT_ANSWER":{"base":3}, ..., "bloom":{"APPLY":1.5,...},
    --  "difficulty":{"EASY":1,"MEDIUM":1.25,"HARD":1.5}} — NULL means the built-in table
    marks_table          JSONB,

    updated_by           UUID,
    created_at           TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP,

    CONSTRAINT ops_pk PRIMARY KEY (psc_id),

    CONSTRAINT ops_psc_fk
        FOREIGN KEY (psc_id) REFERENCES program_semester_courses (id)
        ON DELETE CASCADE
);

CREATE TRIGGER trg_ops_updated_at
    BEFORE UPDATE ON offering_paper_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE question_papers (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    psc_id           UUID          NOT NULL,
    created_by       UUID          NOT NULL,

    title            VARCHAR(255)  NOT NULL,
    instructions     TEXT,
    duration_minutes INTEGER
                     CONSTRAINT qpaper_duration_chk CHECK (duration_minutes IS NULL OR duration_minutes > 0),

    -- Marks limit chosen for this paper (≤ the offering's max_total_marks)
    max_total_marks  NUMERIC(7, 2) NOT NULL
                     CONSTRAINT qpaper_max_marks_chk CHECK (max_total_marks > 0),

    -- Cached sum of item marks; never above max_total_marks
    total_marks      NUMERIC(7, 2) NOT NULL DEFAULT 0,

    status           VARCHAR(5)    NOT NULL DEFAULT 'DRAFT'
                     CONSTRAINT qpaper_status_chk CHECK (status IN ('DRAFT', 'FINAL')),

    created_at       TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP,

    CONSTRAINT qpaper_pk PRIMARY KEY (id),

    CONSTRAINT qpaper_psc_fk
        FOREIGN KEY (psc_id) REFERENCES program_semester_courses (id)
        ON DELETE CASCADE,

    CONSTRAINT qpaper_created_by_fk
        FOREIGN KEY (created_by) REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT qpaper_total_within_limit_chk CHECK (total_marks <= max_total_marks)
);

CREATE INDEX idx_qpaper_psc ON question_papers (psc_id, created_at DESC);

CREATE TRIGGER trg_qpaper_updated_at
    BEFORE UPDATE ON question_papers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE question_paper_items (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    paper_id           UUID          NOT NULL,

    -- Printed in this section order: A (MCQ/MSQ), B (short), C (long)
    section            VARCHAR(6)    NOT NULL
                       CONSTRAINT qpitem_section_chk CHECK (section IN ('CHOICE', 'SHORT', 'LONG')),
    order_index        INTEGER       NOT NULL,

    -- Full question as printed: type, text, options, correctAnswer,
    -- modelAnswer, rubric, bloomLevel, difficulty, cloIds
    question           JSONB         NOT NULL,
    marks              NUMERIC(6, 2) NOT NULL
                       CONSTRAINT qpitem_marks_chk CHECK (marks > 0),

    -- Where it came from (informational; the snapshot is what prints)
    source_question_id UUID,
    source_draft_id    UUID,

    created_at         TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT qpitem_pk PRIMARY KEY (id),

    CONSTRAINT qpitem_paper_fk
        FOREIGN KEY (paper_id) REFERENCES question_papers (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_qpitem_paper ON question_paper_items (paper_id, section, order_index);

CREATE TABLE paper_print_sets (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    paper_id       UUID          NOT NULL,
    set_code       VARCHAR(2)    NOT NULL,
    seed           BIGINT        NOT NULL,

    -- ["<item-id>", ...] in printed order
    question_order JSONB         NOT NULL,
    -- {"<item-id>": ["c","a","d","b"]} printed option order per choice question
    option_orders  JSONB         NOT NULL DEFAULT '{}',

    shuffled_questions BOOLEAN   NOT NULL DEFAULT TRUE,
    shuffled_options   BOOLEAN   NOT NULL DEFAULT TRUE,

    printed_by     UUID,
    printed_at     TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT pps_pk PRIMARY KEY (id),

    CONSTRAINT pps_paper_fk
        FOREIGN KEY (paper_id) REFERENCES question_papers (id)
        ON DELETE CASCADE,

    CONSTRAINT pps_paper_code_uq UNIQUE (paper_id, set_code)
);
