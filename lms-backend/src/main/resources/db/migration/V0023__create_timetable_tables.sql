-- ============================================================
-- V0023__create_timetable_tables.sql
--
-- rooms             — teaching spaces the timetable can place a class in.
-- timetable_entries — one weekly class meeting of an offering: which day,
--                     which hours, which room. Recurs every week of the term.
--
-- Entries are written by the timetable generator (every offering in the open
-- semesters at once, because teachers, rooms and students are shared across
-- programs) and can then be moved or added by hand by an admin.
-- ============================================================

-- ── rooms ─────────────────────────────────────────────────────────────────────

CREATE TABLE rooms (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),

    -- What people call it, e.g. "A-101", "Computing Lab 2"
    name        VARCHAR(60)   NOT NULL,
    building    VARCHAR(80),

    -- Seats; a class is only placed in a room that can hold its roster
    capacity    INTEGER       NOT NULL
                CONSTRAINT rooms_capacity_chk CHECK (capacity > 0),

    -- LECTURE rooms take lectures, LAB rooms take lab sessions
    kind        VARCHAR(10)   NOT NULL DEFAULT 'LECTURE'
                CONSTRAINT rooms_kind_chk CHECK (kind IN ('LECTURE', 'LAB')),

    -- Inactive rooms keep their existing classes but get no new ones
    active      BOOLEAN       NOT NULL DEFAULT TRUE,

    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP,

    CONSTRAINT rooms_pk PRIMARY KEY (id)
);

CREATE UNIQUE INDEX rooms_name_uq ON rooms (lower(name));

CREATE TRIGGER trg_rooms_updated_at
    BEFORE UPDATE ON rooms
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── timetable_entries ─────────────────────────────────────────────────────────

CREATE TABLE timetable_entries (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    psc_id       UUID         NOT NULL,   -- the course offering
    room_id      UUID         NOT NULL,

    kind         VARCHAR(10)  NOT NULL
                 CONSTRAINT timetable_entries_kind_chk CHECK (kind IN ('LECTURE', 'LAB')),

    -- ISO-8601 day of week: 1 = Monday … 7 = Sunday
    day_of_week  SMALLINT     NOT NULL
                 CONSTRAINT timetable_entries_day_chk CHECK (day_of_week BETWEEN 1 AND 7),

    start_time   TIME         NOT NULL,
    end_time     TIME         NOT NULL,

    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP,

    CONSTRAINT timetable_entries_pk PRIMARY KEY (id),

    -- Deleting an offering takes its classes with it
    CONSTRAINT timetable_entries_psc_fk
        FOREIGN KEY (psc_id) REFERENCES program_semester_courses (id)
        ON DELETE CASCADE,

    -- A room in use cannot be deleted; move its classes or deactivate it
    CONSTRAINT timetable_entries_room_fk
        FOREIGN KEY (room_id) REFERENCES rooms (id)
        ON DELETE RESTRICT,

    CONSTRAINT timetable_entries_times_chk CHECK (end_time > start_time)
);

CREATE INDEX idx_timetable_entries_psc_id  ON timetable_entries (psc_id);
CREATE INDEX idx_timetable_entries_room_id ON timetable_entries (room_id);

CREATE TRIGGER trg_timetable_entries_updated_at
    BEFORE UPDATE ON timetable_entries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
