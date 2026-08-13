-- ============================================================
-- V0020__create_material_clo_mappings_table.sql
--
-- material_clo_mappings — links course materials (lectures, readings, videos,
--                         announcements) to the CLOs they teach toward.
--
-- The OBE chain was only half-connected: assessments could be mapped to CLOs
-- (V0013) and CLOs to PLOs (V0007), but the teaching material that is supposed
-- to *deliver* each outcome had no link at all. Without it a course can show
-- CLO attainment while no one can say which material covers a CLO — and a CLO
-- with no material behind it is invisible.
--
-- Mirrors the shape of assignment_clo_mappings / quiz_clo_mappings so the three
-- mapping tables read and query the same way.
-- ============================================================

CREATE TABLE material_clo_mappings (
    material_id UUID          NOT NULL,
    clo_id      UUID          NOT NULL,

    -- Relative contribution of this material to covering the CLO
    -- (0 < weight ≤ 100). NULL means "unweighted / equally weighted".
    weight      NUMERIC(5, 2)
                CONSTRAINT mclo_weight_chk
                CHECK (weight IS NULL OR (weight > 0 AND weight <= 100)),

    created_at  TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT mclo_pk
        PRIMARY KEY (material_id, clo_id),

    CONSTRAINT mclo_material_fk
        FOREIGN KEY (material_id) REFERENCES course_materials (id)
        ON DELETE CASCADE,

    CONSTRAINT mclo_clo_fk
        FOREIGN KEY (clo_id) REFERENCES clos (id)
        ON DELETE CASCADE
);

-- Reverse lookup: every material covering a given CLO (coverage reporting)
CREATE INDEX idx_mclo_clo_id ON material_clo_mappings (clo_id);
