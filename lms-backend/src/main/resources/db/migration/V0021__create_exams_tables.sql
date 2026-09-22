-- ============================================================
-- V0021__create_exams_tables.sql
--
-- Exams — the third assessment type, alongside assignments (V0011) and
-- quizzes (V0012). Unlike those two, an exam is not submitted through the
-- system: students write on paper, the teacher marks the paper, and the
-- marks re-enter the system by photographing the copy's first page.
--
--   exams                      — exam definition (one per offering per sitting)
--   exam_questions             — the marks table's rows: question no + max marks
--   exam_question_clo_mappings — per-question CLO links, for OBE attainment
--   exam_results               — one row per student per exam; the authoritative marks
--   exam_question_marks        — per-question marks making up a result
--   exam_scans                 — capture + extraction audit trail
--
-- ── Why questions map to CLOs, not exams ─────────────────────────────────────
--
-- assignment_clo_mappings and quiz_clo_mappings (V0013) attach a CLO to a whole
-- assessment, because that is the finest granularity those types record. An
-- exam copy's marks table is already per-question, so mapping at the question
-- level costs nothing extra to capture and makes attainment far sharper: "CLO-2
-- is attained at 61%" can point at questions 3 and 4 rather than at one blended
-- exam score. The attainment formula is unchanged from V0013 — each mapped
-- question is one contribution of (marks_obtained / max_marks) * weight.
--
-- ── Why a scan is not a result ───────────────────────────────────────────────
--
-- exam_scans never write marks directly. A scan holds what the extractor read;
-- exam_results holds what a teacher confirmed. Marks are an academic record and
-- the extractor is a probabilistic reader, so every scan is reviewed by the
-- teacher before it becomes a result. The scan row is kept afterwards as
-- evidence: the stored image plus the raw extraction can be re-examined if a
-- student disputes a mark.
-- ============================================================

-- ── exams ─────────────────────────────────────────────────────────────────────

CREATE TABLE exams (
    id          UUID           NOT NULL DEFAULT gen_random_uuid(),
    psc_id      UUID           NOT NULL,
    created_by  UUID           NOT NULL,

    title       VARCHAR(255)   NOT NULL,

    -- Which sitting this is. MAKEUP covers deferred / re-sit papers.
    exam_type   VARCHAR(20)    NOT NULL DEFAULT 'MIDTERM'
                CONSTRAINT exams_type_chk
                CHECK (exam_type IN ('MIDTERM', 'FINAL', 'SESSIONAL', 'MAKEUP')),

    -- The date printed on the copy. Used to cross-check a scanned page against
    -- the exam it is being filed under, so a midterm copy cannot be filed as a
    -- final by opening the wrong exam.
    exam_date   DATE           NOT NULL,

    total_marks NUMERIC(7, 2)  NOT NULL
                CONSTRAINT exams_marks_chk CHECK (total_marks > 0),

    -- DRAFT    — question list still being edited; scanning is refused
    -- OPEN     — accepting scans and manual entry
    -- LOCKED   — marks finalised; no further results may be written
    status      VARCHAR(10)    NOT NULL DEFAULT 'DRAFT'
                CONSTRAINT exams_status_chk
                CHECK (status IN ('DRAFT', 'OPEN', 'LOCKED')),

    created_at  TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP,

    CONSTRAINT exams_pk
        PRIMARY KEY (id),

    CONSTRAINT exams_psc_fk
        FOREIGN KEY (psc_id) REFERENCES program_semester_courses (id)
        ON DELETE CASCADE,

    CONSTRAINT exams_creator_fk
        FOREIGN KEY (created_by) REFERENCES users (id)
        ON DELETE RESTRICT,

    -- Resolving a scanned page by (course code, exam date) must land on exactly
    -- one exam, or the teacher is asked to choose. One sitting of one type per
    -- offering per day keeps that resolution unambiguous in the normal case.
    CONSTRAINT exams_psc_type_date_uq
        UNIQUE (psc_id, exam_type, exam_date)
);

CREATE INDEX idx_exams_psc_id ON exams (psc_id);
CREATE INDEX idx_exams_date   ON exams (exam_date);

CREATE TRIGGER trg_exams_updated_at
    BEFORE UPDATE ON exams
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── exam_questions ────────────────────────────────────────────────────────────

CREATE TABLE exam_questions (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    exam_id     UUID          NOT NULL,

    -- As written on the copy: '1', '2', '3a', 'Q4(b)'. Free text rather than an
    -- integer because part-marks ('2a', '2b') are how real papers are laid out,
    -- and the extractor has to match what it reads against these labels.
    question_no VARCHAR(10)   NOT NULL,

    max_marks   NUMERIC(6, 2) NOT NULL
                CONSTRAINT exam_questions_max_chk CHECK (max_marks > 0),

    order_index INTEGER       NOT NULL
                CONSTRAINT exam_questions_order_chk CHECK (order_index > 0),

    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP,

    CONSTRAINT exam_questions_pk
        PRIMARY KEY (id),

    CONSTRAINT exam_questions_exam_fk
        FOREIGN KEY (exam_id) REFERENCES exams (id)
        ON DELETE CASCADE,

    -- Question labels are the join key between a scanned marks table and the
    -- exam's structure, so they must be unique within the exam.
    CONSTRAINT exam_questions_no_uq
        UNIQUE (exam_id, question_no)
);

CREATE INDEX idx_exam_questions_exam_id ON exam_questions (exam_id, order_index);

CREATE TRIGGER trg_exam_questions_updated_at
    BEFORE UPDATE ON exam_questions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── exam_question_clo_mappings ────────────────────────────────────────────────

CREATE TABLE exam_question_clo_mappings (
    question_id UUID          NOT NULL,
    clo_id      UUID          NOT NULL,

    -- Same semantics as aclo_weight_chk / qclo_weight_chk in V0013:
    -- NULL means "weight this question equally with the CLO's other sources".
    weight      NUMERIC(5, 2)
                CONSTRAINT eqclo_weight_chk
                CHECK (weight IS NULL OR (weight > 0 AND weight <= 100)),

    created_at  TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT eqclo_pk
        PRIMARY KEY (question_id, clo_id),

    CONSTRAINT eqclo_question_fk
        FOREIGN KEY (question_id) REFERENCES exam_questions (id)
        ON DELETE CASCADE,

    CONSTRAINT eqclo_clo_fk
        FOREIGN KEY (clo_id) REFERENCES clos (id)
        ON DELETE CASCADE
);

-- Reverse lookup: every exam question mapped to a CLO (attainment computation)
CREATE INDEX idx_eqclo_clo_id ON exam_question_clo_mappings (clo_id);

-- ── exam_scans ────────────────────────────────────────────────────────────────
--
-- Created before exam_results so a result can carry a FK back to the scan that
-- produced it.

CREATE TABLE exam_scans (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),

    -- NULL until the exam is resolved. A scan captured from the exam's own page
    -- knows its exam immediately; one captured from the "scan a copy" entry
    -- point is resolved from the extracted course code and exam date, which can
    -- fail or be ambiguous.
    exam_id       UUID,

    uploaded_by   UUID          NOT NULL,

    -- The captured page in S3/MinIO. Stored as the object key from a prior
    -- upload to /api/files, the same reference assignment_submissions keeps
    -- (V0011) — the files module owns the object, this module only points at
    -- it. Retained as the evidence behind every mark this scan produced.
    image_key     VARCHAR(512)  NOT NULL,
    image_name    VARCHAR(255),
    image_size    BIGINT
                  CONSTRAINT exam_scans_image_size_chk
                  CHECK (image_size IS NULL OR image_size > 0),

    -- PENDING    — image stored, extraction not finished
    -- EXTRACTED  — extraction succeeded; awaiting teacher review
    -- CONFIRMED  — teacher accepted (possibly after edits); a result exists
    -- FAILED     — extraction errored or the page was unreadable
    -- DISCARDED  — teacher rejected the scan
    status        VARCHAR(10)   NOT NULL DEFAULT 'PENDING'
                  CONSTRAINT exam_scans_status_chk
                  CHECK (status IN ('PENDING', 'EXTRACTED', 'CONFIRMED', 'FAILED', 'DISCARDED')),

    -- ── What the extractor read, before any matching or correction ───────────
    -- Kept as written on the page (not normalised, not resolved) so a dispute
    -- can be traced back to what was actually on the copy.
    read_roll_number  VARCHAR(50),
    read_student_name VARCHAR(255),
    read_course_code  VARCHAR(30),
    read_exam_date    DATE,

    -- Full extractor output: per-question marks, the model's own confidence,
    -- and anything it could not read. Superset of the columns above.
    extraction    JSONB,

    -- Identifier of the extraction model, so a re-read after a model change is
    -- distinguishable from the original.
    extractor     VARCHAR(100),

    -- Student the roll number resolved to; NULL when unresolved or ambiguous.
    matched_student_id UUID,

    -- Human-readable mismatches found while checking the extraction against the
    -- exam (unknown roll number, course code differs, marks above the maximum).
    -- Shown to the teacher on the review screen; not a hard failure.
    warnings      JSONB,

    -- Populated when status = FAILED.
    error_message TEXT,

    confirmed_at  TIMESTAMP,

    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP,

    CONSTRAINT exam_scans_pk
        PRIMARY KEY (id),

    CONSTRAINT exam_scans_exam_fk
        FOREIGN KEY (exam_id) REFERENCES exams (id)
        ON DELETE CASCADE,

    CONSTRAINT exam_scans_uploader_fk
        FOREIGN KEY (uploaded_by) REFERENCES users (id)
        ON DELETE RESTRICT,

    CONSTRAINT exam_scans_student_fk
        FOREIGN KEY (matched_student_id) REFERENCES users (id)
        ON DELETE SET NULL,

    CONSTRAINT exam_scans_confirmed_at_chk
        CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL))
);

-- The teacher's review queue: scans for one exam awaiting confirmation.
CREATE INDEX idx_exam_scans_exam_status ON exam_scans (exam_id, status);

-- "My recent scans", for the capture screen.
CREATE INDEX idx_exam_scans_uploader ON exam_scans (uploaded_by, created_at DESC);

CREATE TRIGGER trg_exam_scans_updated_at
    BEFORE UPDATE ON exam_scans
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── exam_results ──────────────────────────────────────────────────────────────

CREATE TABLE exam_results (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    exam_id        UUID          NOT NULL,
    student_id     UUID          NOT NULL,

    -- Sum of exam_question_marks, denormalised so transcript and listing
    -- queries do not have to aggregate the per-question rows.
    total_obtained NUMERIC(7, 2) NOT NULL
                   CONSTRAINT exam_results_total_chk CHECK (total_obtained >= 0),

    -- SCAN   — captured from a copy and confirmed by a teacher
    -- MANUAL — typed in directly (no copy photographed, or scanning unavailable)
    source         VARCHAR(10)   NOT NULL DEFAULT 'MANUAL'
                   CONSTRAINT exam_results_source_chk
                   CHECK (source IN ('SCAN', 'MANUAL')),

    -- The scan this came from, when source = SCAN. Kept as the audit link to
    -- the photographed copy.
    scan_id        UUID,

    -- The teacher who confirmed these marks — always a person, never the
    -- extractor, because only a confirmed result is written here.
    recorded_by    UUID          NOT NULL,
    recorded_at    TIMESTAMP     NOT NULL DEFAULT now(),

    remarks        TEXT,

    created_at     TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP,

    CONSTRAINT exam_results_pk
        PRIMARY KEY (id),

    CONSTRAINT exam_results_exam_fk
        FOREIGN KEY (exam_id) REFERENCES exams (id)
        ON DELETE CASCADE,

    CONSTRAINT exam_results_student_fk
        FOREIGN KEY (student_id) REFERENCES users (id)
        ON DELETE RESTRICT,

    CONSTRAINT exam_results_scan_fk
        FOREIGN KEY (scan_id) REFERENCES exam_scans (id)
        ON DELETE SET NULL,

    CONSTRAINT exam_results_recorder_fk
        FOREIGN KEY (recorded_by) REFERENCES users (id)
        ON DELETE RESTRICT,

    -- One result per student per exam. A re-scan of the same copy updates this
    -- row rather than adding a second set of marks.
    CONSTRAINT exam_results_exam_student_uq
        UNIQUE (exam_id, student_id),

    CONSTRAINT exam_results_scan_source_chk
        CHECK (source = 'SCAN' OR scan_id IS NULL)
);

CREATE INDEX idx_exam_results_exam_id    ON exam_results (exam_id);
CREATE INDEX idx_exam_results_student_id ON exam_results (student_id);

CREATE TRIGGER trg_exam_results_updated_at
    BEFORE UPDATE ON exam_results
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── exam_question_marks ───────────────────────────────────────────────────────

CREATE TABLE exam_question_marks (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    result_id      UUID          NOT NULL,
    question_id    UUID          NOT NULL,

    -- NULL means the question was not attempted — distinct from a zero, which
    -- means it was attempted and earned nothing. CLO attainment counts both as
    -- zero marks, but the distinction matters when a teacher reviews a copy.
    marks_obtained NUMERIC(6, 2)
                   CONSTRAINT eqm_marks_chk
                   CHECK (marks_obtained IS NULL OR marks_obtained >= 0),

    created_at     TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP,

    CONSTRAINT eqm_pk
        PRIMARY KEY (id),

    CONSTRAINT eqm_result_fk
        FOREIGN KEY (result_id) REFERENCES exam_results (id)
        ON DELETE CASCADE,

    CONSTRAINT eqm_question_fk
        FOREIGN KEY (question_id) REFERENCES exam_questions (id)
        ON DELETE CASCADE,

    CONSTRAINT eqm_result_question_uq
        UNIQUE (result_id, question_id)
);

CREATE INDEX idx_eqm_result_id   ON exam_question_marks (result_id);
CREATE INDEX idx_eqm_question_id ON exam_question_marks (question_id);

CREATE TRIGGER trg_eqm_updated_at
    BEFORE UPDATE ON exam_question_marks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
