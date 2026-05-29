-- ============================================================
-- V0004__create_programs_and_plos_tables.sql
--
-- programs  — academic degree programmes (e.g. "BS Computer Science").
-- plos      — Program Learning Outcomes attached to a programme.
--             CLOs (added later) map to PLOs to enable attainment tracking.
-- ============================================================

-- ── programs ──────────────────────────────────────────────────────────────────

CREATE TABLE programs (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),

    -- Full programme name, e.g. "Bachelor of Science in Computer Science"
    name           VARCHAR(255)  NOT NULL,

    -- Short code used in reports, e.g. "BSCS", "BSEE"
    code           VARCHAR(20)   NOT NULL,

    description    TEXT,

    -- Nominal duration, e.g. 4 for a four-year programme
    duration_years INTEGER       NOT NULL DEFAULT 4
                   CONSTRAINT programs_duration_chk CHECK (duration_years > 0),

    -- ACTIVE = open for new enrolments; INACTIVE = archived
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                   CONSTRAINT programs_status_chk CHECK (status IN ('ACTIVE', 'INACTIVE')),

    created_at     TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP,

    CONSTRAINT programs_pk       PRIMARY KEY (id),
    CONSTRAINT programs_code_uq  UNIQUE (code)
);

CREATE INDEX idx_programs_status ON programs (status);

-- Trigram index enables fast ILIKE search on programme name
CREATE INDEX idx_programs_name_trgm ON programs USING gin (name gin_trgm_ops);

CREATE TRIGGER trg_programs_updated_at
    BEFORE UPDATE ON programs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── plos (Program Learning Outcomes) ─────────────────────────────────────────

CREATE TABLE plos (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    program_id  UUID          NOT NULL,

    -- Short code, unique per programme, e.g. "PLO-1", "PLO-2"
    code        VARCHAR(20)   NOT NULL,

    -- One-line title shown in tables and reports
    title       VARCHAR(255)  NOT NULL,

    -- Full description / bloom's taxonomy level, etc.
    description TEXT,

    -- 1-based display / report ordering within the programme
    order_index INTEGER       NOT NULL
                CONSTRAINT plos_order_chk CHECK (order_index > 0),

    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP,

    CONSTRAINT plos_pk
        PRIMARY KEY (id),

    CONSTRAINT plos_program_fk
        FOREIGN KEY (program_id) REFERENCES programs (id)
        ON DELETE CASCADE,

    -- PLO code must be unique within a programme
    CONSTRAINT plos_program_code_uq
        UNIQUE (program_id, code),

    -- Display order must be unique within a programme
    CONSTRAINT plos_program_order_uq
        UNIQUE (program_id, order_index)
);

CREATE INDEX idx_plos_program_id ON plos (program_id);

CREATE TRIGGER trg_plos_updated_at
    BEFORE UPDATE ON plos
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
