-- ============================================================
-- V0015__create_grading_scales_tables.sql
--
-- grading_scales         — named grade-band schemes (global or program-specific).
-- grading_scale_entries  — individual letter-grade bands within a scale.
--
-- Scale resolution order (application layer):
--   1. Program-specific scale (program_id = student's program AND is_default = TRUE)
--   2. Global default scale   (program_id IS NULL         AND is_default = TRUE)
--
-- A transcript is always rendered against the scale that was active at the
-- time of semester closure (the scale id is snapshotted into the transcript JSONB).
--
-- ── Typical entries for a 4.0 GPA scale ─────────────────────────────────────
--   grade_letter | min_pct | max_pct | grade_points | order_index
--   A+           |  95     | 100     |  4.00        |  1
--   A            |  90     |  95     |  4.00        |  2
--   A-           |  85     |  90     |  3.70        |  3
--   B+           |  80     |  85     |  3.30        |  4
--   ...
--   F            |   0     |  50     |  0.00        | 12
--
-- Range semantics: min_percentage ≤ score < max_percentage
-- (top entry: max_percentage = 100 is inclusive at the application layer).
-- ============================================================

-- ── grading_scales ────────────────────────────────────────────────────────────

CREATE TABLE grading_scales (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),

    -- NULL = system-wide default; non-NULL = override for a specific program
    program_id  UUID,

    name        VARCHAR(120) NOT NULL,

    -- Marks this scale as the one to use for the given scope (program or global)
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP,

    CONSTRAINT grading_scales_pk
        PRIMARY KEY (id),

    CONSTRAINT grading_scales_program_fk
        FOREIGN KEY (program_id) REFERENCES programs (id)
        ON DELETE CASCADE
);

-- At most one global default scale (program_id IS NULL)
CREATE UNIQUE INDEX idx_grading_scales_global_default
    ON grading_scales (is_default)
    WHERE is_default = TRUE AND program_id IS NULL;

-- At most one default scale per program
CREATE UNIQUE INDEX idx_grading_scales_program_default
    ON grading_scales (program_id)
    WHERE is_default = TRUE AND program_id IS NOT NULL;

CREATE INDEX idx_grading_scales_program_id ON grading_scales (program_id);

CREATE TRIGGER trg_grading_scales_updated_at
    BEFORE UPDATE ON grading_scales
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── grading_scale_entries ─────────────────────────────────────────────────────

CREATE TABLE grading_scale_entries (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    scale_id      UUID          NOT NULL,

    grade_letter  VARCHAR(3)    NOT NULL,   -- e.g. 'A+', 'A', 'B-', 'F'

    -- Percentage range: min ≤ score < max  (application treats max of top entry as inclusive)
    min_percentage NUMERIC(5, 2) NOT NULL
                   CONSTRAINT gse_min_pct_chk CHECK (min_percentage >= 0),
    max_percentage NUMERIC(5, 2) NOT NULL
                   CONSTRAINT gse_max_pct_chk CHECK (max_percentage <= 100),

    -- GPA quality points awarded for this grade
    grade_points   NUMERIC(4, 2) NOT NULL
                   CONSTRAINT gse_points_chk CHECK (grade_points >= 0),

    -- Display / sort order within the scale (1 = highest)
    order_index    INTEGER       NOT NULL
                   CONSTRAINT gse_order_chk CHECK (order_index > 0),

    created_at     TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP,

    CONSTRAINT gse_pk
        PRIMARY KEY (id),

    CONSTRAINT gse_scale_fk
        FOREIGN KEY (scale_id) REFERENCES grading_scales (id)
        ON DELETE CASCADE,

    -- Range must be a valid interval
    CONSTRAINT gse_range_chk
        CHECK (min_percentage < max_percentage),

    -- No two entries in the same scale may share a letter grade
    CONSTRAINT gse_letter_uq
        UNIQUE (scale_id, grade_letter),

    -- No two entries may occupy the same display position
    CONSTRAINT gse_order_uq
        UNIQUE (scale_id, order_index)
);

-- Lookup: find the letter grade for a given score percentage
CREATE INDEX idx_gse_scale_range ON grading_scale_entries (scale_id, min_percentage, max_percentage);

CREATE TRIGGER trg_gse_updated_at
    BEFORE UPDATE ON grading_scale_entries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
