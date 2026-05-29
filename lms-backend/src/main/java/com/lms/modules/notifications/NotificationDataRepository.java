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

    public record UserRow(UUID id, String email) {}

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
                SELECT u.id, u.email
                FROM   users u
                JOIN   assignments a ON a.created_by = u.id
                WHERE  a.id = :assignmentId
                """;
        List<UserRow> rows = jdbc.query(sql, Map.of("assignmentId", assignmentId),
                (rs, i) -> new UserRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("email")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Find the creator (teacher/assistant) of a quiz so they can be notified
     * when a student submits.
     */
    public Optional<UserRow> findQuizCreator(UUID quizId) {
        String sql = """
                SELECT u.id, u.email
                FROM   users u
                JOIN   quizzes q ON q.created_by = u.id
                WHERE  q.id = :quizId
                """;
        List<UserRow> rows = jdbc.query(sql, Map.of("quizId", quizId),
                (rs, i) -> new UserRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("email")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Find all active/completed students enrolled in a semester's courses.
     * Used by the semester CLOSED/REOPENED handler.
     */
    public List<UserRow> findEnrolledStudentsByProgramSemester(UUID semesterId) {
        String sql = """
                SELECT DISTINCT u.id, u.email
                FROM   users u
                JOIN   enrollments e ON e.student_id = u.id
                JOIN   program_semester_courses psc ON psc.id = e.psc_id
                WHERE  psc.semester_id = :semesterId
                AND    e.status IN ('ACTIVE', 'COMPLETED')
                """;
        return jdbc.query(sql, Map.of("semesterId", semesterId),
                (rs, i) -> new UserRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("email")));
    }
}
