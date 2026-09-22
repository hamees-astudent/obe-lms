package com.lms.modules.exams;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Plain JDBC repository for the exams module's cross-module reads.
 *
 * <p>Follows the same convention as {@code TranscriptDataRepository}: JPA stays
 * inside the module, and anything that has to touch {@code users},
 * {@code courses}, {@code enrollments} or {@code clos} goes through
 * {@link NamedParameterJdbcTemplate} instead of an ORM join across a module
 * boundary.
 */
@Repository
@RequiredArgsConstructor
public class ExamDataRepository {

    private final NamedParameterJdbcTemplate jdbc;

    // ── Inner data records ──────────────────────────────────────────────────

    /** A student matched from a roll number, with the name to show the teacher. */
    public record StudentRow(UUID studentId, String name, String studentNumber) {}

    /** Identity of a course offering, for validating a scanned page against it. */
    public record OfferingRow(UUID pscId, UUID courseId, String courseCode,
                              String courseName, UUID teacherId, UUID semesterId) {}

    public record CloRow(UUID id, String code, String title) {}

    // ── Student resolution ──────────────────────────────────────────────────

    /**
     * Resolve a roll number to the student enrolled in this offering.
     *
     * <p>Scoped to the offering deliberately. Roll numbers are unique across the
     * institution, so a global lookup would succeed for a student who never took
     * this course and file their marks under someone else's exam. Restricting
     * the match to the roster turns that into "not found", which the teacher
     * sees and can correct.
     */
    public Optional<StudentRow> findEnrolledStudentByRollNumber(UUID pscId, String rollNumber) {
        String sql = """
                SELECT u.id, u.name, sp.student_number
                FROM   student_profiles sp
                JOIN   users u        ON u.id = sp.user_id
                JOIN   enrollments e  ON e.student_id = u.id
                WHERE  UPPER(TRIM(sp.student_number)) = UPPER(TRIM(:rollNumber))
                AND    e.psc_id      = :pscId
                AND    e.course_role = 'STUDENT'
                AND    e.status IN ('ACTIVE', 'COMPLETED')
                """;
        List<StudentRow> rows = jdbc.query(sql,
                Map.of("pscId", pscId, "rollNumber", rollNumber),
                (rs, i) -> new StudentRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("name"),
                        rs.getString("student_number")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Every student on this offering's roster, for the manual-entry picker. */
    public List<StudentRow> findRoster(UUID pscId) {
        String sql = """
                SELECT u.id, u.name, sp.student_number
                FROM   enrollments e
                JOIN   users u             ON u.id = e.student_id
                LEFT JOIN student_profiles sp ON sp.user_id = u.id
                WHERE  e.psc_id      = :pscId
                AND    e.course_role = 'STUDENT'
                AND    e.status IN ('ACTIVE', 'COMPLETED')
                ORDER BY sp.student_number NULLS LAST, u.name
                """;
        return jdbc.query(sql, Map.of("pscId", pscId),
                (rs, i) -> new StudentRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("name"),
                        rs.getString("student_number")));
    }

    /** How many students are on the roster — the listing needs the size, not the rows. */
    public int countRoster(UUID pscId) {
        String sql = """
                SELECT COUNT(*)
                FROM   enrollments
                WHERE  psc_id      = :pscId
                AND    course_role = 'STUDENT'
                AND    status IN ('ACTIVE', 'COMPLETED')
                """;
        Integer count = jdbc.queryForObject(sql, Map.of("pscId", pscId), Integer.class);
        return count != null ? count : 0;
    }

    /** Whether a student is on this offering's roster. */
    public boolean isEnrolled(UUID pscId, UUID studentId) {
        String sql = """
                SELECT COUNT(*)
                FROM   enrollments
                WHERE  psc_id       = :pscId
                AND    student_id   = :studentId
                AND    course_role  = 'STUDENT'
                AND    status IN ('ACTIVE', 'COMPLETED')
                """;
        Integer count = jdbc.queryForObject(sql,
                Map.of("pscId", pscId, "studentId", studentId), Integer.class);
        return count != null && count > 0;
    }

    // ── Offering resolution ─────────────────────────────────────────────────

    public Optional<OfferingRow> findOffering(UUID pscId) {
        String sql = """
                SELECT psc.id AS psc_id, c.id AS course_id, c.code, c.name,
                       psc.teacher_id, psc.semester_id
                FROM   program_semester_courses psc
                JOIN   courses c ON c.id = psc.course_id
                WHERE  psc.id = :pscId
                """;
        List<OfferingRow> rows = jdbc.query(sql, Map.of("pscId", pscId), OFFERING_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Offerings a user may record marks for — as primary teacher, or through a
     * TEACHER / ASSISTANT enrollment row (the course-assistant path).
     *
     * <p>Used to resolve a standalone scan: the extracted course code is matched
     * only against offerings this teacher actually runs, so one teacher's scan
     * can never resolve onto another's course.
     */
    public List<OfferingRow> findOfferingsForTeacher(UUID teacherId) {
        String sql = """
                SELECT DISTINCT psc.id AS psc_id, c.id AS course_id, c.code, c.name,
                       psc.teacher_id, psc.semester_id
                FROM   program_semester_courses psc
                JOIN   courses c ON c.id = psc.course_id
                LEFT JOIN enrollments e
                       ON e.psc_id = psc.id
                      AND e.student_id = :teacherId
                      AND e.course_role IN ('TEACHER', 'ASSISTANT')
                      AND e.status = 'ACTIVE'
                WHERE  psc.teacher_id = :teacherId
                   OR  e.id IS NOT NULL
                """;
        return jdbc.query(sql, Map.of("teacherId", teacherId), OFFERING_MAPPER);
    }

    /** Whether a user may record marks for this offering. */
    public boolean canManageOffering(UUID pscId, UUID userId) {
        String sql = """
                SELECT COUNT(*)
                FROM   program_semester_courses psc
                LEFT JOIN enrollments e
                       ON e.psc_id = psc.id
                      AND e.student_id = :userId
                      AND e.course_role IN ('TEACHER', 'ASSISTANT')
                      AND e.status = 'ACTIVE'
                WHERE  psc.id = :pscId
                AND   (psc.teacher_id = :userId OR e.id IS NOT NULL)
                """;
        Integer count = jdbc.queryForObject(sql,
                Map.of("pscId", pscId, "userId", userId), Integer.class);
        return count != null && count > 0;
    }

    // ── CLOs ────────────────────────────────────────────────────────────────

    /** CLOs defined on the course behind an offering — the mappable set. */
    public List<CloRow> findClosByPscId(UUID pscId) {
        String sql = """
                SELECT cl.id, cl.code, cl.title
                FROM   clos cl
                JOIN   program_semester_courses psc ON psc.course_id = cl.course_id
                WHERE  psc.id = :pscId
                ORDER BY cl.order_index
                """;
        return jdbc.query(sql, Map.of("pscId", pscId),
                (rs, i) -> new CloRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("code"),
                        rs.getString("title")));
    }

    /** Whether a CLO belongs to the course behind an offering. */
    public boolean cloBelongsToOffering(UUID pscId, UUID cloId) {
        String sql = """
                SELECT COUNT(*)
                FROM   clos cl
                JOIN   program_semester_courses psc ON psc.course_id = cl.course_id
                WHERE  psc.id = :pscId AND cl.id = :cloId
                """;
        Integer count = jdbc.queryForObject(sql,
                Map.of("pscId", pscId, "cloId", cloId), Integer.class);
        return count != null && count > 0;
    }

    // ── Shared mappers ──────────────────────────────────────────────────────

    private static final org.springframework.jdbc.core.RowMapper<OfferingRow> OFFERING_MAPPER =
            (rs, i) -> new OfferingRow(
                    UUID.fromString(rs.getString("psc_id")),
                    UUID.fromString(rs.getString("course_id")),
                    rs.getString("code"),
                    rs.getString("name"),
                    UUID.fromString(rs.getString("teacher_id")),
                    UUID.fromString(rs.getString("semester_id")));
}
