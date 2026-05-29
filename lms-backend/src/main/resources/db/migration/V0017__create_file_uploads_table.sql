-- ============================================================
-- V0017__create_file_uploads_table.sql
--
-- file_uploads — central registry of every object stored in S3/MinIO.
--
-- Design notes:
--   • object_key follows the convention: <context>/<file-id>/<sanitised-name>
--     e.g. submissions/3f4a.../report.pdf
--          materials/9b1c.../lecture-1.mp4
--   • context / context_id are nullable; they let query paths find all files
--     belonging to a given entity (e.g. all files for an assignment submission).
--   • No FK on context_id — the column is polymorphic; integrity is enforced
--     at the application layer and by cascade-deleting when the owning entity
--     is removed.
-- ============================================================

CREATE TABLE file_uploads (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    object_key     VARCHAR(500)  NOT NULL,   -- S3/MinIO object key
    original_name  VARCHAR(255)  NOT NULL,   -- filename shown to the user
    content_type   VARCHAR(100)  NOT NULL,   -- MIME type
    file_size      BIGINT        NOT NULL,   -- bytes

    -- Who uploaded the file
    uploaded_by    UUID          NOT NULL,

    -- Which module / entity owns this file (nullable for standalone uploads)
    context        VARCHAR(50),   -- e.g. 'submissions', 'materials', 'profiles'
    context_id     UUID,          -- owning entity id

    created_at     TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT file_uploads_pk
        PRIMARY KEY (id),

    CONSTRAINT file_uploads_uploader_fk
        FOREIGN KEY (uploaded_by) REFERENCES users (id)
        ON DELETE RESTRICT,

    CONSTRAINT file_uploads_object_key_uq
        UNIQUE (object_key)
);

-- Look up all files for a specific owning entity
CREATE INDEX idx_file_uploads_context
    ON file_uploads (context, context_id)
    WHERE context_id IS NOT NULL;

-- List all uploads by a user
CREATE INDEX idx_file_uploads_uploaded_by
    ON file_uploads (uploaded_by, created_at DESC);
