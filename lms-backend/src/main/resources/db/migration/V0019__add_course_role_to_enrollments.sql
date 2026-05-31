-- ============================================================
-- V0019__add_course_role_to_enrollments.sql
--
-- Adds course_role to enrollments so any user (TEACHER, ASSISTANT,
-- STUDENT, ADMIN) can be added to a course offering with an explicit
-- role for that course only.
-- ============================================================

ALTER TABLE enrollments
    ADD COLUMN course_role VARCHAR(20) NOT NULL DEFAULT 'STUDENT'
        CONSTRAINT enrollments_course_role_chk
        CHECK (course_role IN ('TEACHER', 'ASSISTANT', 'STUDENT', 'ADMIN'));
