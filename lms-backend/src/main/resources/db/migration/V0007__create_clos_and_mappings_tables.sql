-- ============================================================
-- V0007__create_clos_and_mappings_tables.sql
--
-- clos             — Course Learning Outcomes, defined at the course-catalog
--                   level so they are reused across semester offerings.
-- clo_plo_mappings — Many-to-many alignment of CLOs to PLOs.
--                   Program context comes from plos.program_id, allowing
--                   the same CLO to map to different PLOs in different
--                   programmes that share the same course.
--
-- Attainment pipeline (semester closure):
--   PSC → course → CLOs
--       ↓ assessment_clo_mappings
--   student marks per CLO  →  CLO attainment %
--       ↓ clo_plo_mappings (filtered to student's programme)
--   PLO attainment %  →  transcript snapshot
-- ============================================================

-- ── clos (Course Learning Outcomes) ──────────────────────────────────────────

CREATE TABLE clos (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    course_id   UUID          NOT NULL,

    -- Short code, unique within the course, e.g. "CLO-1", "CLO-2"
    code        VARCHAR(20)   NOT NULL,

    -- One-line title shown in assessment forms and reports
    title       VARCHAR(255)  NOT NULL,

    -- Full description / cognitive level (Bloom's taxonomy)
    description TEXT,

    -- 1-based display ordering within the course
    order_index INTEGER       NOT NULL
                CONSTRAINT clos_order_chk CHECK (order_index > 0),

    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP,

    CONSTRAINT clos_pk
        PRIMARY KEY (id),

    CONSTRAINT clos_course_fk
        FOREIGN KEY (course_id) REFERENCES courses (id)
        ON DELETE CASCADE,

    -- CLO code must be unique within a course
    CONSTRAINT clos_course_code_uq
        UNIQUE (course_id, code),

    -- Display order must be unique within a course
    CONSTRAINT clos_course_order_uq
        UNIQUE (course_id, order_index)
);

CREATE INDEX idx_clos_course_id ON clos (course_id);

CREATE TRIGGER trg_clos_updated_at
    BEFORE UPDATE ON clos
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── clo_plo_mappings ──────────────────────────────────────────────────────────

CREATE TABLE clo_plo_mappings (
    clo_id      UUID      NOT NULL,
    plo_id      UUID      NOT NULL,   -- plos.program_id carries the programme context

    -- Optional contribution weight (0–100 %).
    -- NULL means the mapping is qualitative only; non-null enables weighted PLO attainment.
    weight      NUMERIC(5, 2)
                CONSTRAINT clo_plo_weight_chk CHECK (weight IS NULL OR (weight > 0 AND weight <= 100)),

    created_at  TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT clo_plo_mappings_pk
        PRIMARY KEY (clo_id, plo_id),

    CONSTRAINT clo_plo_clo_fk
        FOREIGN KEY (clo_id) REFERENCES clos (id)
        ON DELETE CASCADE,

    CONSTRAINT clo_plo_plo_fk
        FOREIGN KEY (plo_id) REFERENCES plos (id)
        ON DELETE CASCADE
);

-- Reverse lookup: all CLOs that contribute to a given PLO
CREATE INDEX idx_clo_plo_plo_id ON clo_plo_mappings (plo_id);
