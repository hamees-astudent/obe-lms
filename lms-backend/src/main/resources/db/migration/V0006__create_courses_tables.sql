-- ============================================================
-- V0006__create_courses_tables.sql
--
-- courses                 — course catalog (definition, independent of semester)
-- program_semester_courses — one "offering" of a course in a given semester,
--                            assigned to a primary teacher  (often abbreviated PSC)
-- course_assistants        — TAs / assistant instructors on a PSC
-- ============================================================

-- ── courses ───────────────────────────────────────────────────────────────────

CREATE TABLE courses (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),

    -- Institutional course code, e.g. "CS101", "EE201"
    code          VARCHAR(20)  NOT NULL,

    name          VARCHAR(255) NOT NULL,
    description   TEXT,

    credit_hours  SMALLINT     NOT NULL
                  CONSTRAINT courses_credits_chk CHECK (credit_hours > 0),

    -- ACTIVE = can be offered in a semester  |  INACTIVE = archived
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                  CONSTRAINT courses_status_chk CHECK (status IN ('ACTIVE', 'INACTIVE')),

    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP,

    CONSTRAINT courses_pk      PRIMARY KEY (id),
    CONSTRAINT courses_code_uq UNIQUE (code)
);

CREATE INDEX idx_courses_status ON courses (status);

-- Trigram index for name search in the admin / teacher course browser
CREATE INDEX idx_courses_name_trgm ON courses USING gin (name gin_trgm_ops);

CREATE TRIGGER trg_courses_updated_at
    BEFORE UPDATE ON courses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── program_semester_courses (PSC) ────────────────────────────────────────────

CREATE TABLE program_semester_courses (
    id           UUID     NOT NULL DEFAULT gen_random_uuid(),
    semester_id  UUID     NOT NULL,   -- also carries program context via semesters.program_id
    course_id    UUID     NOT NULL,
    teacher_id   UUID     NOT NULL,   -- primary instructor (TEACHER role)

    -- 0 = unlimited
    max_capacity INTEGER  NOT NULL DEFAULT 0
                 CONSTRAINT psc_capacity_chk CHECK (max_capacity >= 0),

    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP,

    CONSTRAINT psc_pk
        PRIMARY KEY (id),

    CONSTRAINT psc_semester_fk
        FOREIGN KEY (semester_id) REFERENCES semesters (id)
        ON DELETE RESTRICT,

    CONSTRAINT psc_course_fk
        FOREIGN KEY (course_id) REFERENCES courses (id)
        ON DELETE RESTRICT,

    CONSTRAINT psc_teacher_fk
        FOREIGN KEY (teacher_id) REFERENCES users (id)
        ON DELETE RESTRICT,

    -- A course may be offered only once per semester
    CONSTRAINT psc_semester_course_uq UNIQUE (semester_id, course_id)
);

CREATE INDEX idx_psc_semester_id  ON program_semester_courses (semester_id);
CREATE INDEX idx_psc_course_id    ON program_semester_courses (course_id);
CREATE INDEX idx_psc_teacher_id   ON program_semester_courses (teacher_id);

CREATE TRIGGER trg_psc_updated_at
    BEFORE UPDATE ON program_semester_courses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── course_assistants ─────────────────────────────────────────────────────────

CREATE TABLE course_assistants (
    psc_id     UUID      NOT NULL,   -- program_semester_course
    user_id    UUID      NOT NULL,   -- user with ASSISTANT or TEACHER role

    created_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT course_assistants_pk
        PRIMARY KEY (psc_id, user_id),

    CONSTRAINT course_assistants_psc_fk
        FOREIGN KEY (psc_id) REFERENCES program_semester_courses (id)
        ON DELETE CASCADE,

    CONSTRAINT course_assistants_user_fk
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

-- Reverse lookup: all courses a given user is assisting
CREATE INDEX idx_course_assistants_user_id ON course_assistants (user_id);
