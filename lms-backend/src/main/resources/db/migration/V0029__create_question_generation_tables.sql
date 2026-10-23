-- ============================================================
-- V0029__create_question_generation_tables.sql
--
-- question_generation_jobs  — a teacher's request to generate questions from
--                             chosen materials/topics with a type mix,
--                             difficulty, Bloom levels, CLOs and a marks limit.
-- generated_question_drafts — the questions a job produced, each with its
--                             suggested CLOs, Bloom level and marks, for the
--                             teacher to review, edit, accept or reject before
--                             anything reaches a quiz or paper.
-- ============================================================

CREATE TABLE question_generation_jobs (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    psc_id        UUID          NOT NULL,
    requested_by  UUID          NOT NULL,

    -- {"counts":{"MCQ":5,...},"difficulty":"MEDIUM","bloomLevels":[...],
    --  "targetCloIds":[...],"maxTotalMarks":50,"instructions":"..."}
    config        JSONB         NOT NULL DEFAULT '{}',

    material_ids  UUID[]        NOT NULL DEFAULT '{}',
    topic_ids     UUID[]        NOT NULL DEFAULT '{}',

    status        VARCHAR(8)    NOT NULL DEFAULT 'PENDING'
                  CONSTRAINT qgen_status_chk
                  CHECK (status IN ('PENDING', 'RUNNING', 'READY', 'FAILED')),

    -- LLM: written by the local model · TEMPLATE: built from the text by rules
    -- · HYBRID: model output topped up by templates
    mode          VARCHAR(8)
                  CONSTRAINT qgen_mode_chk CHECK (mode IS NULL OR mode IN ('LLM', 'TEMPLATE', 'HYBRID')),

    error         TEXT,
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    finished_at   TIMESTAMP,

    CONSTRAINT qgen_pk PRIMARY KEY (id),

    CONSTRAINT qgen_psc_fk
        FOREIGN KEY (psc_id) REFERENCES program_semester_courses (id)
        ON DELETE CASCADE,

    CONSTRAINT qgen_requested_by_fk
        FOREIGN KEY (requested_by) REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_qgen_psc ON question_generation_jobs (psc_id, created_at DESC);

CREATE TABLE generated_question_drafts (
    id                UUID          NOT NULL DEFAULT gen_random_uuid(),
    job_id            UUID          NOT NULL,
    order_index       INTEGER       NOT NULL,

    type              VARCHAR(20)   NOT NULL
                      CONSTRAINT qdraft_type_chk
                      CHECK (type IN ('MCQ', 'MSQ', 'SHORT_ANSWER', 'LONG_ANSWER')),
    question_text     TEXT          NOT NULL,
    options           JSONB         NOT NULL DEFAULT '[]',
    correct_answer    JSONB         NOT NULL DEFAULT '[]',
    model_answer      TEXT,
    rubric            JSONB         NOT NULL DEFAULT '[]',
    explanation       TEXT,

    marks             NUMERIC(6, 2) NOT NULL
                      CONSTRAINT qdraft_marks_chk CHECK (marks > 0),
    bloom_level       VARCHAR(12),
    difficulty        VARCHAR(6),

    -- CLO suggestion: chosen CLOs plus every candidate's similarity score
    suggested_clo_ids UUID[]        NOT NULL DEFAULT '{}',
    clo_scores        JSONB         NOT NULL DEFAULT '[]',
    clo_rationale     TEXT,

    -- How the Bloom level was decided, e.g. "verb: compare"
    bloom_source      TEXT,

    -- LLM or TEMPLATE
    source            VARCHAR(8)    NOT NULL DEFAULT 'TEMPLATE',

    -- Chunks the question was written from (for citations and grounding)
    source_chunk_ids  UUID[]        NOT NULL DEFAULT '{}',

    status            VARCHAR(8)    NOT NULL DEFAULT 'PENDING'
                      CONSTRAINT qdraft_status_chk CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),

    edited            BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP,

    CONSTRAINT qdraft_pk PRIMARY KEY (id),

    CONSTRAINT qdraft_job_fk
        FOREIGN KEY (job_id) REFERENCES question_generation_jobs (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_qdraft_job ON generated_question_drafts (job_id, order_index);

CREATE TRIGGER trg_qdraft_updated_at
    BEFORE UPDATE ON generated_question_drafts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
