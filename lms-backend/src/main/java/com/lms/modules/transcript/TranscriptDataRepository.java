package com.lms.modules.transcript;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Plain JDBC repository for all cross-module queries.
 * Uses {@link NamedParameterJdbcTemplate} (no JPA) to respect module boundaries
 * while still querying other modules' tables when strictly necessary.
 */
@Repository
@RequiredArgsConstructor
public class TranscriptDataRepository {

    private final NamedParameterJdbcTemplate jdbc;

    // ── Inner data records ──────────────────────────────────────────────────

    public record CourseRow(UUID pscId, UUID courseId, String courseCode, String courseName, int creditHours) {}

    public record StudentRow(String name, String studentNumber) {}

    public record SemesterRow(String semesterName, String programName) {}

    public record CloRow(UUID id, String code, String title) {}

    public record AssessmentContrib(double marksObtained, double totalMarks, Double weight) {}

    public record PloRow(UUID cloId, UUID ploId, String ploCode, String ploTitle) {}

    public record AttendanceRow(long attended, long total) {}

    public record GpaRow(double semesterGpa, int creditHours) {}

    // ── Queries ─────────────────────────────────────────────────────────────

    /** Resolve admin's UUID from their email address. */
    public Optional<UUID> findUserIdByEmail(String email) {
        String sql = "SELECT id FROM users WHERE email = :email";
        List<UUID> rows = jdbc.query(sql,
                Map.of("email", email),
                (rs, i) -> UUID.fromString(rs.getString("id")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** All active/completed student IDs enrolled in any course of the given semester. */
    public List<UUID> findEnrolledStudentIds(UUID semesterId) {
        String sql = """
                SELECT DISTINCT e.student_id
                FROM   enrollments e
                JOIN   program_semester_courses psc ON psc.id = e.psc_id
                WHERE  psc.semester_id = :semesterId
                AND    e.status IN ('ACTIVE', 'COMPLETED')
                """;
        return jdbc.query(sql,
                Map.of("semesterId", semesterId),
                (rs, i) -> UUID.fromString(rs.getString("student_id")));
    }

    /** Courses (PSC rows) for a student in a semester. */
    public List<CourseRow> findStudentCoursesForSemester(UUID semesterId, UUID studentId) {
        String sql = """
                SELECT psc.id   AS psc_id,
                       c.id     AS course_id,
                       c.code   AS course_code,
                       c.name   AS course_name,
                       c.credit_hours
                FROM   enrollments e
                JOIN   program_semester_courses psc ON psc.id = e.psc_id
                JOIN   courses c ON c.id = psc.course_id
                WHERE  psc.semester_id = :semesterId
                AND    e.student_id    = :studentId
                AND    e.status IN ('ACTIVE', 'COMPLETED')
                """;
        return jdbc.query(sql,
                Map.of("semesterId", semesterId, "studentId", studentId),
                (rs, i) -> new CourseRow(
                        UUID.fromString(rs.getString("psc_id")),
                        UUID.fromString(rs.getString("course_id")),
                        rs.getString("course_code"),
                        rs.getString("course_name"),
                        rs.getInt("credit_hours")));
    }

    /** Student's name and student number. */
    public Optional<StudentRow> findStudentInfo(UUID studentId) {
        String sql = """
                SELECT u.name, sp.student_number
                FROM   users u
                LEFT JOIN student_profiles sp ON sp.user_id = u.id
                WHERE  u.id = :studentId
                """;
        List<StudentRow> rows = jdbc.query(sql,
                Map.of("studentId", studentId),
                (rs, i) -> new StudentRow(
                        rs.getString("name"),
                        rs.getString("student_number")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Semester name and program name. */
    public Optional<SemesterRow> findSemesterInfo(UUID semesterId) {
        String sql = """
                SELECT s.name AS semester_name,
                       p.name AS program_name
                FROM   semesters s
                JOIN   programs p ON p.id = s.program_id
                WHERE  s.id = :semesterId
                """;
        List<SemesterRow> rows = jdbc.query(sql,
                Map.of("semesterId", semesterId),
                (rs, i) -> new SemesterRow(
                        rs.getString("semester_name"),
                        rs.getString("program_name")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** CLOs defined for the course that owns this PSC row. */
    public List<CloRow> findClosByPscId(UUID pscId) {
        String sql = """
                SELECT cl.id, cl.code, cl.title
                FROM   clos cl
                JOIN   program_semester_courses psc ON psc.course_id = cl.course_id
                WHERE  psc.id = :pscId
                ORDER  BY cl.order_index
                """;
        return jdbc.query(sql,
                Map.of("pscId", pscId),
                (rs, i) -> new CloRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("code"),
                        rs.getString("title")));
    }

    /** Attendance summary (attended + total countable sessions) for a student in a PSC. */
    public AttendanceRow findAttendanceSummary(UUID pscId, UUID studentId) {
        String sql = """
                SELECT COUNT(*) FILTER (WHERE ar.status IN ('PRESENT','LATE')) AS attended,
                       COUNT(*) FILTER (WHERE ar.status  != 'EXCUSED')          AS total
                FROM   attendance_records ar
                JOIN   attendance_sessions s ON s.id = ar.session_id
                WHERE  s.psc_id        = :pscId
                AND    ar.student_id   = :studentId
                """;
        return jdbc.queryForObject(sql,
                Map.of("pscId", pscId, "studentId", studentId),
                (rs, i) -> new AttendanceRow(
                        rs.getLong("attended"),
                        rs.getLong("total")));
    }

    /** Sum of total_marks across all assignments for a PSC. */
    public double findAssignmentsTotalMarks(UUID pscId) {
        String sql = "SELECT COALESCE(SUM(total_marks), 0) FROM assignments WHERE psc_id = :pscId";
        Double result = jdbc.queryForObject(sql, Map.of("pscId", pscId), Double.class);
        return result != null ? result : 0.0;
    }

    /** Sum of total_marks across all quizzes for a PSC. */
    public double findQuizzesTotalMarks(UUID pscId) {
        String sql = "SELECT COALESCE(SUM(total_marks), 0) FROM quizzes WHERE psc_id = :pscId";
        Double result = jdbc.queryForObject(sql, Map.of("pscId", pscId), Double.class);
        return result != null ? result : 0.0;
    }

    /** Sum of marks_obtained from graded assignment submissions for a student in a PSC. */
    public double findStudentAssignmentMarks(UUID pscId, UUID studentId) {
        String sql = """
                SELECT COALESCE(SUM(sub.marks_obtained), 0)
                FROM   assignment_submissions sub
                JOIN   assignments a ON a.id = sub.assignment_id
                WHERE  a.psc_id          = :pscId
                AND    sub.student_id    = :studentId
                AND    sub.status        = 'GRADED'
                """;
        Double result = jdbc.queryForObject(sql,
                Map.of("pscId", pscId, "studentId", studentId), Double.class);
        return result != null ? result : 0.0;
    }

    /** Sum of scores from submitted quiz submissions for a student in a PSC. */
    public double findStudentQuizMarks(UUID pscId, UUID studentId) {
        String sql = """
                SELECT COALESCE(SUM(qs.score), 0)
                FROM   quiz_submissions qs
                JOIN   quizzes q ON q.id = qs.quiz_id
                WHERE  q.psc_id           = :pscId
                AND    qs.student_id      = :studentId
                AND    qs.submitted_at IS NOT NULL
                """;
        Double result = jdbc.queryForObject(sql,
                Map.of("pscId", pscId, "studentId", studentId), Double.class);
        return result != null ? result : 0.0;
    }

    /**
     * Assignment-side CLO contributions: marks obtained, total marks, and weight
     * for all assignments in the PSC that are mapped to the given CLO.
     */
    public List<AssessmentContrib> findAssignmentCloContributions(UUID pscId, UUID studentId, UUID cloId) {
        String sql = """
                SELECT COALESCE(sub.marks_obtained, 0) AS marks_obtained,
                       a.total_marks,
                       acm.weight
                FROM   assignment_clo_mappings acm
                JOIN   assignments a ON a.id = acm.assignment_id AND a.psc_id = :pscId
                LEFT JOIN assignment_submissions sub
                       ON sub.assignment_id = a.id
                      AND sub.student_id    = :studentId
                      AND sub.status        = 'GRADED'
                WHERE  acm.clo_id = :cloId
                """;
        return jdbc.query(sql,
                Map.of("pscId", pscId, "studentId", studentId, "cloId", cloId),
                (rs, i) -> new AssessmentContrib(
                        rs.getDouble("marks_obtained"),
                        rs.getDouble("total_marks"),
                        (Double) rs.getObject("weight")));
    }

    /**
     * Quiz-side CLO contributions: marks obtained, total marks, and weight
     * for all quizzes in the PSC that are mapped to the given CLO.
     */
    public List<AssessmentContrib> findQuizCloContributions(UUID pscId, UUID studentId, UUID cloId) {
        String sql = """
                SELECT COALESCE(qs.score, 0) AS marks_obtained,
                       q.total_marks,
                       qcm.weight
                FROM   quiz_clo_mappings qcm
                JOIN   quizzes q ON q.id = qcm.quiz_id AND q.psc_id = :pscId
                LEFT JOIN quiz_submissions qs
                       ON qs.quiz_id       = q.id
                      AND qs.student_id    = :studentId
                      AND qs.submitted_at IS NOT NULL
                WHERE  qcm.clo_id = :cloId
                """;
        return jdbc.query(sql,
                Map.of("pscId", pscId, "studentId", studentId, "cloId", cloId),
                (rs, i) -> new AssessmentContrib(
                        rs.getDouble("marks_obtained"),
                        rs.getDouble("total_marks"),
                        (Double) rs.getObject("weight")));
    }

    /**
     * PLO mappings for a batch of CLO IDs.
     * Uses IN clause — only called after deduplication so the list is reasonably sized.
     */
    public List<PloRow> findPloMappingsByCloIds(List<UUID> cloIds) {
        if (cloIds.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = """
                SELECT cpm.clo_id,
                       cpm.plo_id,
                       p.code  AS plo_code,
                       p.title AS plo_title
                FROM   clo_plo_mappings cpm
                JOIN   plos p ON p.id = cpm.plo_id
                WHERE  cpm.clo_id IN (:cloIds)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cloIds", cloIds.stream().map(UUID::toString).toList());
        return jdbc.query(sql, params,
                (rs, i) -> new PloRow(
                        UUID.fromString(rs.getString("clo_id")),
                        UUID.fromString(rs.getString("plo_id")),
                        rs.getString("plo_code"),
                        rs.getString("plo_title")));
    }

    /**
     * Previous semester transcripts for the same student + program
     * (used to compute cumulative GPA excluding the current semester).
     */
    public List<GpaRow> findPreviousGpaRows(UUID studentId, UUID programId, UUID excludeSemesterId) {
        String sql = """
                SELECT semester_gpa, total_credit_hours
                FROM   transcripts
                WHERE  student_id  = :studentId
                AND    program_id  = :programId
                AND    semester_id != :excludeSemesterId
                """;
        return jdbc.query(sql,
                Map.of("studentId", studentId,
                       "programId", programId,
                       "excludeSemesterId", excludeSemesterId),
                (rs, i) -> new GpaRow(
                        rs.getDouble("semester_gpa"),
                        rs.getInt("total_credit_hours")));
    }
}
