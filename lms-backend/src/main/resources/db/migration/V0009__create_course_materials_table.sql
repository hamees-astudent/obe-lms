-- ============================================================
-- V0009__create_course_materials_table.sql
--
-- course_materials — the knowledge-base / content feed for a course offering.
-- Supports six types:
--   DOCUMENT     — uploaded file stored in S3/MinIO
--   URL          — external hyperlink
--   ASSIGNMENT   — links to an assignments row (added in a later migration)
--   QUIZ         — links to a quizzes row (added in a later migration)
--   ANNOUNCEMENT — rich-text notice to enrolled students
--   VIDEO_LINK   — embedded / linked video (YouTube, Vimeo, etc.)
--
-- The `content` JSONB column holds the type-specific payload so that the
-- common columns (title, visibility, ordering) are queryable without parsing
-- JSON, while flexible per-type data stays schema-free.
--
-- Expected JSONB shapes per type:
--   DOCUMENT     { "objectKey": "materials/<uuid>/<filename>",
--                  "fileName": "...", "fileSize": 1234, "mimeType": "..." }
--   URL          { "url": "https://...", "linkText": "..." }
--   ASSIGNMENT   { "assignmentId": "<uuid>" }
--   QUIZ         { "quizId": "<uuid>" }
--   ANNOUNCEMENT { "body": "<html or plain text>" }
--   VIDEO_LINK   { "url": "https://...", "platform": "YOUTUBE|VIMEO|OTHER",
--                  "durationSeconds": 3600 }
-- ============================================================

CREATE TABLE course_materials (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),

    -- The course offering this material belongs to
    psc_id        UUID         NOT NULL,

    -- Teacher or assistant who posted the material
    uploaded_by   UUID         NOT NULL,

    type          VARCHAR(20)  NOT NULL
                  CONSTRAINT course_materials_type_chk
                  CHECK (type IN ('DOCUMENT','URL','ASSIGNMENT','QUIZ','ANNOUNCEMENT','VIDEO_LINK')),

    title         VARCHAR(255) NOT NULL,
    description   TEXT,

    -- Type-specific payload (see shapes in header comment)
    content       JSONB        NOT NULL DEFAULT '{}',

    -- FALSE = draft / hidden from students
    visible       BOOLEAN      NOT NULL DEFAULT TRUE,

    -- Teacher-controlled display order within the course feed (gaps allowed)
    order_index   INTEGER      NOT NULL DEFAULT 0,

    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP,

    CONSTRAINT course_materials_pk
        PRIMARY KEY (id),

    CONSTRAINT course_materials_psc_fk
        FOREIGN KEY (psc_id) REFERENCES program_semester_courses (id)
        ON DELETE CASCADE,

    CONSTRAINT course_materials_uploader_fk
        FOREIGN KEY (uploaded_by) REFERENCES users (id)
        ON DELETE RESTRICT
);

-- ── Indexes ───────────────────────────────────────────────────────────────────

-- Student / teacher feed: all materials for a course ordered by position
CREATE INDEX idx_course_materials_psc_id
    ON course_materials (psc_id, order_index);

-- Visible-only feed (the most common query path)
CREATE INDEX idx_course_materials_psc_visible
    ON course_materials (psc_id, order_index)
    WHERE visible = TRUE;

-- Filter by type within a course (e.g. list only ANNOUNCEMENTs)
CREATE INDEX idx_course_materials_psc_type
    ON course_materials (psc_id, type);

-- GIN index on content JSONB — enables containment queries such as:
--   WHERE content @> '{"objectKey": "materials/..."}'
--   WHERE content @> '{"assignmentId": "<uuid>"}'
CREATE INDEX idx_course_materials_content_gin
    ON course_materials USING gin (content);

CREATE TRIGGER trg_course_materials_updated_at
    BEFORE UPDATE ON course_materials
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
