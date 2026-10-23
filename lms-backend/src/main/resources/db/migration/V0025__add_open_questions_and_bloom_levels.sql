-- ============================================================
-- V0025__add_open_questions_and_bloom_levels.sql
--
-- Quiz questions gain two written-answer types and the OBE metadata the
-- question generator fills in:
--
--   SHORT_ANSWER / LONG_ANSWER  answered in free text, marked against a model
--                               answer and a rubric (not auto-graded)
--   bloom_level                 cognitive level the question targets
--   difficulty                  EASY | MEDIUM | HARD
--   ai_generated                came from the question generator
--
-- quiz_question_clo_mappings maps individual questions to CLOs. The existing
-- quiz-level quiz_clo_mappings still drives transcript attainment; when
-- questions are saved from the generator the quiz-level rows are derived from
-- these (weighted by marks).
--
-- clos.bloom_level records the level a CLO is written at, which the CLO
-- suggester compares against a question's level.
-- ============================================================

ALTER TABLE clos
    ADD COLUMN bloom_level VARCHAR(12)
        CONSTRAINT clos_bloom_chk
        CHECK (bloom_level IS NULL OR bloom_level IN
               ('REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE', 'EVALUATE', 'CREATE'));

-- ── quiz_questions ────────────────────────────────────────────────────────────

ALTER TABLE quiz_questions DROP CONSTRAINT qquestion_type_chk;

ALTER TABLE quiz_questions
    ALTER COLUMN type TYPE VARCHAR(20);

ALTER TABLE quiz_questions
    ADD CONSTRAINT qquestion_type_chk
        CHECK (type IN ('MCQ', 'MSQ', 'SHORT_ANSWER', 'LONG_ANSWER'));

ALTER TABLE quiz_questions
    ADD COLUMN bloom_level  VARCHAR(12)
        CONSTRAINT qquestion_bloom_chk
        CHECK (bloom_level IS NULL OR bloom_level IN
               ('REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE', 'EVALUATE', 'CREATE')),
    ADD COLUMN difficulty   VARCHAR(6)
        CONSTRAINT qquestion_difficulty_chk
        CHECK (difficulty IS NULL OR difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    -- Written-answer questions: what a full-marks answer says
    ADD COLUMN model_answer TEXT,
    -- [{"criterion":"Defines X","marks":2,"keywords":["x","y"]}, ...]
    ADD COLUMN rubric       JSONB   NOT NULL DEFAULT '[]',
    ADD COLUMN ai_generated BOOLEAN NOT NULL DEFAULT FALSE;

-- Choice questions carry options; written ones carry a model answer instead
ALTER TABLE quiz_questions
    ADD CONSTRAINT qquestion_open_shape_chk
        CHECK (
            type IN ('MCQ', 'MSQ')
            OR (jsonb_array_length(options) = 0
                AND jsonb_array_length(correct_answer) = 0
                AND model_answer IS NOT NULL)
        );

-- ── quiz_question_clo_mappings ────────────────────────────────────────────────

CREATE TABLE quiz_question_clo_mappings (
    question_id UUID          NOT NULL,
    clo_id      UUID          NOT NULL,

    weight      NUMERIC(5, 2)
                CONSTRAINT qqclo_weight_chk
                CHECK (weight IS NULL OR (weight > 0 AND weight <= 100)),

    created_at  TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT qqclo_pk PRIMARY KEY (question_id, clo_id),

    CONSTRAINT qqclo_question_fk
        FOREIGN KEY (question_id) REFERENCES quiz_questions (id)
        ON DELETE CASCADE,

    CONSTRAINT qqclo_clo_fk
        FOREIGN KEY (clo_id) REFERENCES clos (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_qqclo_clo_id ON quiz_question_clo_mappings (clo_id);
