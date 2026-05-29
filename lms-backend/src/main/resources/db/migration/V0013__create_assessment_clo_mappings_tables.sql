-- ============================================================
-- V0013__create_assessment_clo_mappings_tables.sql
--
-- assignment_clo_mappings — links assignments to CLOs.
-- quiz_clo_mappings       — links quizzes to CLOs.
--
-- Two separate tables (one per assessment type) preserve full FK referential
-- integrity while keeping the schema explicit.
--
-- ── CLO Attainment formula (application layer) ───────────────────────────────
--
-- For each CLO c attached to a course offering (psc):
--
--   attainment(c) =
--     Σ [ (student_marks / total_marks) * weight ]  for each assessment mapped to c
--     ─────────────────────────────────────────────
--                    Σ [ weight ]
--
-- If weight is NULL on all mappings for a CLO, the service treats every
-- assessment as equally weighted (weight = 1).
--
-- The result feeds into the semester transcript snapshot (V0016).
-- ============================================================

-- ── assignment_clo_mappings ───────────────────────────────────────────────────

CREATE TABLE assignment_clo_mappings (
    assignment_id UUID          NOT NULL,
    clo_id        UUID          NOT NULL,

    -- Relative contribution of this assignment to the CLO (0 < weight ≤ 100).
    -- NULL means "use equal weighting across all assessments mapped to this CLO".
    weight        NUMERIC(5, 2)
                  CONSTRAINT aclo_weight_chk
                  CHECK (weight IS NULL OR (weight > 0 AND weight <= 100)),

    created_at    TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT aclo_pk
        PRIMARY KEY (assignment_id, clo_id),

    CONSTRAINT aclo_assignment_fk
        FOREIGN KEY (assignment_id) REFERENCES assignments (id)
        ON DELETE CASCADE,

    CONSTRAINT aclo_clo_fk
        FOREIGN KEY (clo_id) REFERENCES clos (id)
        ON DELETE CASCADE
);

-- Reverse lookup: all assignments mapped to a given CLO (attainment computation)
CREATE INDEX idx_aclo_clo_id ON assignment_clo_mappings (clo_id);

-- ── quiz_clo_mappings ─────────────────────────────────────────────────────────

CREATE TABLE quiz_clo_mappings (
    quiz_id    UUID          NOT NULL,
    clo_id     UUID          NOT NULL,

    weight     NUMERIC(5, 2)
               CONSTRAINT qclo_weight_chk
               CHECK (weight IS NULL OR (weight > 0 AND weight <= 100)),

    created_at TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT qclo_pk
        PRIMARY KEY (quiz_id, clo_id),

    CONSTRAINT qclo_quiz_fk
        FOREIGN KEY (quiz_id) REFERENCES quizzes (id)
        ON DELETE CASCADE,

    CONSTRAINT qclo_clo_fk
        FOREIGN KEY (clo_id) REFERENCES clos (id)
        ON DELETE CASCADE
);

-- Reverse lookup: all quizzes mapped to a given CLO
CREATE INDEX idx_qclo_clo_id ON quiz_clo_mappings (clo_id);
