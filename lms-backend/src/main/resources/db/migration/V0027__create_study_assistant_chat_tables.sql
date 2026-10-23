-- ============================================================
-- V0027__create_study_assistant_chat_tables.sql
--
-- chat_sessions — a student's conversation with the study assistant about one
--                 course offering. Answers are drawn only from that offering's
--                 visible materials.
-- chat_messages — the turns of a session. Assistant turns keep the citations
--                 (material, page/slide) they were grounded in.
-- ============================================================

CREATE TABLE chat_sessions (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    student_id  UUID          NOT NULL,
    psc_id      UUID          NOT NULL,
    title       VARCHAR(200)  NOT NULL DEFAULT 'New conversation',
    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP,

    CONSTRAINT chat_sessions_pk PRIMARY KEY (id),

    CONSTRAINT chat_sessions_student_fk
        FOREIGN KEY (student_id) REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT chat_sessions_psc_fk
        FOREIGN KEY (psc_id) REFERENCES program_semester_courses (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_chat_sessions_student
    ON chat_sessions (student_id, psc_id, updated_at DESC);

CREATE TRIGGER trg_chat_sessions_updated_at
    BEFORE UPDATE ON chat_sessions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE chat_messages (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    session_id  UUID          NOT NULL,

    role        VARCHAR(10)   NOT NULL
                CONSTRAINT chat_messages_role_chk CHECK (role IN ('USER', 'ASSISTANT')),

    content     TEXT          NOT NULL,

    -- [{"ref":"S1","chunkId":"...","materialId":"...","title":"...",
    --   "locationType":"SLIDE","locationStart":12,"locationEnd":12}]
    citations   JSONB         NOT NULL DEFAULT '[]',

    -- TRUE when the assistant declined: nothing in the material covered the question
    refused     BOOLEAN       NOT NULL DEFAULT FALSE,

    -- LLM: written by the local model · EXTRACTIVE: sentences picked from the material
    mode        VARCHAR(10)
                CONSTRAINT chat_messages_mode_chk CHECK (mode IS NULL OR mode IN ('LLM', 'EXTRACTIVE')),

    -- An LLM answer with a sentence no cited source supports
    unverified  BOOLEAN       NOT NULL DEFAULT FALSE,

    created_at  TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chat_messages_pk PRIMARY KEY (id),

    CONSTRAINT chat_messages_session_fk
        FOREIGN KEY (session_id) REFERENCES chat_sessions (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_chat_messages_session ON chat_messages (session_id, created_at);
