-- ============================================================
-- V0024__create_material_ingestion_tables.sql
--
-- material_ingestions — one row per DOCUMENT material: whether its text has
--                       been read, split into chunks and indexed yet.
-- material_chunks     — the material's text in ~350-word passages, each tied
--                       to the page, slide or section it came from so the
--                       study assistant can cite "Slide 12" or "p. 4".
-- material_topics     — the topics a material covers, with the page/slide
--                       range each spans. Teachers pick topics to generate
--                       questions on; students pick them for mock quizzes.
--
-- Retrieval is PostgreSQL full-text search: `tsv` is a generated column
-- (heading weighted above body text), searched through a GIN index. No
-- external service is involved.
-- ============================================================

-- ── material_ingestions ───────────────────────────────────────────────────────

CREATE TABLE material_ingestions (
    material_id    UUID          NOT NULL,

    -- PENDING     queued after upload or edit
    -- PROCESSING  being read now
    -- READY       chunks are searchable
    -- FAILED      could not be read (error says why, e.g. a scanned PDF)
    -- UNSUPPORTED not a PDF, PPTX or DOCX
    status         VARCHAR(12)   NOT NULL DEFAULT 'PENDING'
                   CONSTRAINT ming_status_chk
                   CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED', 'UNSUPPORTED')),

    error          TEXT,
    page_count     INTEGER,
    chunk_count    INTEGER,

    -- Hash of the file last read, so an edit that keeps the same file is not re-read
    content_sha256 VARCHAR(64),

    attempts       INTEGER       NOT NULL DEFAULT 0,
    started_at     TIMESTAMP,
    finished_at    TIMESTAMP,
    created_at     TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP,

    CONSTRAINT ming_pk PRIMARY KEY (material_id),

    CONSTRAINT ming_material_fk
        FOREIGN KEY (material_id) REFERENCES course_materials (id)
        ON DELETE CASCADE
);

CREATE TRIGGER trg_ming_updated_at
    BEFORE UPDATE ON material_ingestions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── material_chunks ───────────────────────────────────────────────────────────

CREATE TABLE material_chunks (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    material_id    UUID          NOT NULL,

    -- Denormalised from the material so retrieval filters by offering without a join
    psc_id         UUID          NOT NULL,

    chunk_index    INTEGER       NOT NULL,

    -- Where the passage sits in the original file
    location_type  VARCHAR(8)    NOT NULL
                   CONSTRAINT mchunk_loc_type_chk CHECK (location_type IN ('PAGE', 'SLIDE', 'SECTION')),
    location_start INTEGER       NOT NULL,
    location_end   INTEGER       NOT NULL,

    -- Nearest heading or slide title above the passage
    heading        TEXT,
    content        TEXT          NOT NULL,

    -- Noun-phrase key terms (OpenNLP), used for topics, distractors and grounding checks
    key_phrases    TEXT[]        NOT NULL DEFAULT '{}',

    token_estimate INTEGER       NOT NULL DEFAULT 0,

    tsv            TSVECTOR GENERATED ALWAYS AS (
                       setweight(to_tsvector('english', coalesce(heading, '')), 'A') ||
                       setweight(to_tsvector('english', content), 'B')
                   ) STORED,

    created_at     TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT mchunk_pk PRIMARY KEY (id),

    CONSTRAINT mchunk_material_fk
        FOREIGN KEY (material_id) REFERENCES course_materials (id)
        ON DELETE CASCADE,

    CONSTRAINT mchunk_location_chk CHECK (location_end >= location_start),

    CONSTRAINT mchunk_material_index_uq UNIQUE (material_id, chunk_index)
);

CREATE INDEX idx_mchunk_tsv    ON material_chunks USING GIN (tsv);
CREATE INDEX idx_mchunk_psc_id ON material_chunks (psc_id);

-- ── material_topics ───────────────────────────────────────────────────────────

CREATE TABLE material_topics (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    material_id    UUID          NOT NULL,
    psc_id         UUID          NOT NULL,

    name           VARCHAR(200)  NOT NULL,
    summary        TEXT,

    -- Page/slide/section range the topic spans within the material
    location_start INTEGER       NOT NULL,
    location_end   INTEGER       NOT NULL,

    order_index    INTEGER       NOT NULL DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT mtopic_pk PRIMARY KEY (id),

    CONSTRAINT mtopic_material_fk
        FOREIGN KEY (material_id) REFERENCES course_materials (id)
        ON DELETE CASCADE,

    CONSTRAINT mtopic_location_chk CHECK (location_end >= location_start)
);

CREATE INDEX idx_mtopic_psc_name ON material_topics (psc_id, lower(name));
CREATE INDEX idx_mtopic_material ON material_topics (material_id, order_index);
