-- ============================================================
-- V0022__create_cohorts_tables.sql
--
-- cohorts        — a named, reusable group of students (an intake, a section,
--                  "BSCS Fall 2024 – A"), kept so a whole batch can be enrolled
--                  in an offering in one step instead of one student at a time.
-- cohort_members — which students belong to a cohort.
--
-- A cohort is only a list of people. Enrolling it writes ordinary rows into
-- enrollments (V0008) — no enrollment remembers which cohort it came from, so
-- changing or deleting a cohort afterwards never touches anyone's enrollments.
-- ============================================================

-- ── cohorts ───────────────────────────────────────────────────────────────────

CREATE TABLE cohorts (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),

    name        VARCHAR(120)  NOT NULL,
    description TEXT,

    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP,

    CONSTRAINT cohorts_pk PRIMARY KEY (id)
);

-- Names are what admins pick cohorts by, so two differing only in case would
-- be indistinguishable in the picker.
CREATE UNIQUE INDEX cohorts_name_uq ON cohorts (lower(name));

CREATE TRIGGER trg_cohorts_updated_at
    BEFORE UPDATE ON cohorts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── cohort_members ────────────────────────────────────────────────────────────

CREATE TABLE cohort_members (
    cohort_id   UUID       NOT NULL,
    student_id  UUID       NOT NULL,   -- user with STUDENT role

    created_at  TIMESTAMP  NOT NULL DEFAULT now(),

    CONSTRAINT cohort_members_pk
        PRIMARY KEY (cohort_id, student_id),

    CONSTRAINT cohort_members_cohort_fk
        FOREIGN KEY (cohort_id) REFERENCES cohorts (id)
        ON DELETE CASCADE,

    CONSTRAINT cohort_members_student_fk
        FOREIGN KEY (student_id) REFERENCES users (id)
        ON DELETE CASCADE
);

-- Reverse lookup: which cohorts a student belongs to
CREATE INDEX idx_cohort_members_student_id ON cohort_members (student_id);
