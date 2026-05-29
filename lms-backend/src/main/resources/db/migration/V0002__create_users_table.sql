-- ============================================================
-- V0002__create_users_table.sql
--
-- Creates the core users table.  One row per user regardless of role.
-- Role-specific profile data is held in extension tables added later
-- (student_profiles, teacher_profiles).
-- ============================================================

CREATE TABLE users (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    -- Matches com.lms.shared.Role enum values
    role          VARCHAR(20)  NOT NULL
                  CONSTRAINT users_role_chk
                  CHECK (role IN ('ADMIN', 'TEACHER', 'ASSISTANT', 'STUDENT')),

    -- ACTIVE | INACTIVE | SUSPENDED
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                  CONSTRAINT users_status_chk
                  CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),

    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP,

    CONSTRAINT users_pk        PRIMARY KEY (id),
    CONSTRAINT users_email_uq  UNIQUE (email)
);

-- ── Indexes ──────────────────────────────────────────────────────────────────

-- Login lookup by email is the hot path; covering the password_hash
-- avoids a heap fetch for the authentication query.
CREATE INDEX idx_users_email    ON users (email);

-- Allows efficient listing/filtering of users by role (admin dashboard).
CREATE INDEX idx_users_role     ON users (role);

-- Allows efficient filtering of active/inactive/suspended users.
CREATE INDEX idx_users_status   ON users (status);

-- Trigram index on name for ILIKE / full-text search in the admin UI.
CREATE INDEX idx_users_name_trgm ON users USING gin (name gin_trgm_ops);

-- ── Trigger: keep updated_at current ─────────────────────────────────────────

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
