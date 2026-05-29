-- ============================================================
-- V0016__create_transcripts_table.sql
--
-- transcripts — one immutable row per student per semester, generated when
--               an admin closes the semester (semester.status → CLOSED).
--
-- The row is WRITE-ONCE.  If an admin corrects grades after closure and
-- re-generates the transcript, the service DELETEs the old row and INSERTs
-- a new one (audit trail preserved via generated_at / generated_by).
--
-- semester_gpa and cumulative_gpa are denormalised from the snapshot so the
-- DB can answer ranking / filter queries (dean's list, CGPA ≥ 3.0, …) without
-- extracting JSONB on every row.
--
-- ── snapshot JSONB shape ─────────────────────────────────────────────────────
-- {
--   "studentName"       : "...",
--   "studentNumber"     : "...",
--   "programName"       : "...",
--   "semesterName"      : "...",
--   "gradingScaleName"  : "...",
--   "semesterGpa"       : 3.50,
--   "cumulativeGpa"     : 3.42,
--   "totalCreditHours"  : 18,
--   "earnedCreditHours" : 18,
--   "courses": [
--     {
--       "pscId"                : "<uuid>",
--       "courseCode"           : "CS301",
--       "courseName"           : "...",
--       "creditHours"          : 3,
--       "attendancePercentage" : 88.5,
--       "totalMarks"           : 100,
--       "marksObtained"        : 85.0,
--       "percentage"           : 85.0,
--       "gradeLetter"          : "B+",
--       "gradePoints"          : 3.30,
--       "cloAttainment": [
--         { "cloCode": "CLO1", "cloTitle": "...", "attainmentPercentage": 82.0 }
--       ]
--     }
--   ],
--   "ploAttainment": [
--     { "ploCode": "PLO1", "ploTitle": "...", "attainmentPercentage": 79.5 }
--   ]
-- }
-- ============================================================

CREATE TABLE transcripts (
    id                UUID          NOT NULL DEFAULT gen_random_uuid(),

    student_id        UUID          NOT NULL,
    semester_id       UUID          NOT NULL,
    program_id        UUID          NOT NULL,

    -- Scale used when computing letter grades; snapshotted so future scale
    -- changes do not alter historical transcripts
    grading_scale_id  UUID          NOT NULL,

    -- Full immutable snapshot written at semester closure
    snapshot          JSONB         NOT NULL,

    -- Denormalised for fast DB-level ranking / filtering queries
    semester_gpa      NUMERIC(4, 2) NOT NULL
                      CONSTRAINT transcripts_sgpa_chk  CHECK (semester_gpa  >= 0),
    cumulative_gpa    NUMERIC(4, 2) NOT NULL
                      CONSTRAINT transcripts_cgpa_chk  CHECK (cumulative_gpa >= 0),

    -- Total credit hours attempted this semester
    total_credit_hours   INTEGER    NOT NULL
                         CONSTRAINT transcripts_tch_chk CHECK (total_credit_hours >= 0),

    -- Credit hours actually earned (passed courses only)
    earned_credit_hours  INTEGER    NOT NULL
                         CONSTRAINT transcripts_ech_chk CHECK (earned_credit_hours >= 0),

    -- Metadata
    generated_at      TIMESTAMP     NOT NULL DEFAULT now(),
    generated_by      UUID          NOT NULL,   -- admin who closed the semester

    created_at        TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT transcripts_pk
        PRIMARY KEY (id),

    CONSTRAINT transcripts_student_fk
        FOREIGN KEY (student_id) REFERENCES users (id)
        ON DELETE RESTRICT,

    CONSTRAINT transcripts_semester_fk
        FOREIGN KEY (semester_id) REFERENCES semesters (id)
        ON DELETE RESTRICT,

    CONSTRAINT transcripts_program_fk
        FOREIGN KEY (program_id) REFERENCES programs (id)
        ON DELETE RESTRICT,

    CONSTRAINT transcripts_scale_fk
        FOREIGN KEY (grading_scale_id) REFERENCES grading_scales (id)
        ON DELETE RESTRICT,

    CONSTRAINT transcripts_generator_fk
        FOREIGN KEY (generated_by) REFERENCES users (id)
        ON DELETE RESTRICT,

    -- One transcript per student per semester
    CONSTRAINT transcripts_student_semester_uq
        UNIQUE (student_id, semester_id),

    -- Earned cannot exceed total
    CONSTRAINT transcripts_credit_hours_chk
        CHECK (earned_credit_hours <= total_credit_hours)
);

-- Student transcript history (most recent first)
CREATE INDEX idx_transcripts_student     ON transcripts (student_id, generated_at DESC);

-- All transcripts for a semester (bulk export / semester closure report)
CREATE INDEX idx_transcripts_semester    ON transcripts (semester_id);

-- GPA-based ranking within a program+semester (dean's list, academic standing)
CREATE INDEX idx_transcripts_gpa_ranking ON transcripts (program_id, semester_id, semester_gpa DESC);

-- GIN index for JSONB containment queries
-- e.g. find all transcripts where a specific CLO attainment appears
CREATE INDEX idx_transcripts_snapshot_gin ON transcripts USING GIN (snapshot);
