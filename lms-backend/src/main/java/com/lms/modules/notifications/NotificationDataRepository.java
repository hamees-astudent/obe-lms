package com.lms.modules.notifications;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Cross-module JDBC queries needed to resolve recipient IDs from event context.
 */
@Repository
@RequiredArgsConstructor
public class NotificationDataRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public record UserRow(UUID id, String email, String name) {}

    /** Find a user's UUID by email (used to resolve current-user identity). */
    public Optional<UUID> findUserIdByEmail(String email) {
        List<UUID> rows = jdbc.query(
                "SELECT id FROM users WHERE email = :email",
                Map.of("email", email),
                (rs, i) -> UUID.fromString(rs.getString("id")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Find the creator (teacher/assistant) of an assignment so they can be
     * notified when a student submits.
     */
    public Optional<UserRow> findAssignmentCreator(UUID assignmentId) {
        String sql = """
                SELECT u.id, u.email, u.name
                FROM   users u
                JOIN   assignments a ON a.created_by = u.id
                WHERE  a.id = :assignmentId
                """;
        List<UserRow> rows = jdbc.query(sql, Map.of("assignmentId", assignmentId),
                (rs, i) -> new UserRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("email"),
                        rs.getString("name")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Find the creator (teacher/assistant) of a quiz so they can be notified
     * when a student submits.
     */
    public Optional<UserRow> findQuizCreator(UUID quizId) {
        String sql = """
                SELECT u.id, u.email, u.name
                FROM   users u
                JOIN   quizzes q ON q.created_by = u.id
                WHERE  q.id = :quizId
                """;
        List<UserRow> rows = jdbc.query(sql, Map.of("quizId", quizId),
                (rs, i) -> new UserRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("email"),
                        rs.getString("name")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Roster of an offering: every actively-enrolled student, with the course
     * identity needed for the notification text.
     *
     * <p>Backs the fan-out for "new assignment / quiz / material" — one event
     * per class rather than one per student.
     */
    public List<UserRow> findActiveStudentsByPsc(UUID pscId) {
        String sql = """
                SELECT u.id, u.email, u.name
                FROM   users u
                JOIN   enrollments e ON e.student_id = u.id
                WHERE  e.psc_id      = :pscId
                AND    e.status      = 'ACTIVE'
                AND    e.course_role = 'STUDENT'
                """;
        return jdbc.query(sql, Map.of("pscId", pscId),
                (rs, i) -> new UserRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("email"),
                        rs.getString("name")));
    }

    /** Course code and name for an offering, for notification copy. */
    public Optional<CourseRow> findCourseByPsc(UUID pscId) {
        String sql = """
                SELECT c.code, c.name
                FROM   program_semester_courses psc
                JOIN   courses c ON c.id = psc.course_id
                WHERE  psc.id = :pscId
                """;
        List<CourseRow> rows = jdbc.query(sql, Map.of("pscId", pscId),
                (rs, i) -> new CourseRow(rs.getString("code"), rs.getString("name")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public record CourseRow(String code, String name) {}

    /**
     * Find all active/completed students enrolled in a semester's courses.
     * Used by the semester CLOSED/REOPENED handler.
     */
    public List<UserRow> findEnrolledStudentsByProgramSemester(UUID semesterId) {
        String sql = """
                SELECT DISTINCT u.id, u.email, u.name
                FROM   users u
                JOIN   enrollments e ON e.student_id = u.id
                JOIN   program_semester_courses psc ON psc.id = e.psc_id
                WHERE  psc.semester_id = :semesterId
                AND    e.status IN ('ACTIVE', 'COMPLETED')
                """;
        return jdbc.query(sql, Map.of("semesterId", semesterId),
                (rs, i) -> new UserRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("email"),
                        rs.getString("name")));
    }
}
