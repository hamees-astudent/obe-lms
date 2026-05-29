-- ============================================================
-- V0003__create_profile_extension_tables.sql
--
-- 1-to-1 extension tables for role-specific user data.
-- Both tables use user_id as their primary key (not a surrogate),
-- enforcing exactly one profile row per user.
--
-- Cascade-delete ensures profiles are cleaned up when a user is removed.
-- The set_updated_at() trigger function was created in V0002.
-- ============================================================

-- ── student_profiles ─────────────────────────────────────────────────────────

CREATE TABLE student_profiles (
    user_id            UUID         NOT NULL,

    -- Institutional student ID printed on ID card / transcripts
    student_number     VARCHAR(50)  NOT NULL,

    date_of_birth      DATE,
    phone              VARCHAR(30),
    address            TEXT,

    -- S3/MinIO object key for the profile picture (NULL = not uploaded yet)
    profile_picture_key VARCHAR(512),

    -- Date the student was first enrolled in the institution
    enrollment_date    DATE,

    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP,

    CONSTRAINT student_profiles_pk
        PRIMARY KEY (user_id),

    CONSTRAINT student_profiles_user_fk
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT student_profiles_number_uq
        UNIQUE (student_number)
);

CREATE INDEX idx_student_profiles_number
    ON student_profiles (student_number);

CREATE TRIGGER trg_student_profiles_updated_at
    BEFORE UPDATE ON student_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── teacher_profiles ─────────────────────────────────────────────────────────

CREATE TABLE teacher_profiles (
    user_id             UUID         NOT NULL,

    -- Staff / employee ID
    employee_number     VARCHAR(50)  NOT NULL,

    department          VARCHAR(255),

    -- e.g. "Professor", "Associate Professor", "Lecturer", "Lab Instructor"
    designation         VARCHAR(100),

    phone               VARCHAR(30),

    -- S3/MinIO object key for the profile picture
    profile_picture_key VARCHAR(512),

    joining_date        DATE,
    bio                 TEXT,

    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP,

    CONSTRAINT teacher_profiles_pk
        PRIMARY KEY (user_id),

    CONSTRAINT teacher_profiles_user_fk
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT teacher_profiles_number_uq
        UNIQUE (employee_number)
);

CREATE INDEX idx_teacher_profiles_number
    ON teacher_profiles (employee_number);

CREATE INDEX idx_teacher_profiles_department
    ON teacher_profiles (department);

CREATE TRIGGER trg_teacher_profiles_updated_at
    BEFORE UPDATE ON teacher_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
