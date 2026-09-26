package com.lms.modules.timetable;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Plain JDBC reads for the timetable module, following the convention of
 * {@code ExamDataRepository}: anything touching {@code program_semester_courses},
 * {@code enrollments}, {@code users} or {@code cohort_members} goes through
 * {@link NamedParameterJdbcTemplate} rather than an ORM join across modules.
 *
 * <p>"Open" means an offering in an OPEN semester of an ACTIVE program — the
 * term being taught now, across every program at once.
 */
@Repository
@RequiredArgsConstructor
public class TimetableDataRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** An offering in the current term, with what the timetable shows about it. */
    public record OfferingRow(UUID pscId, String courseCode, String courseName, int creditHours,
                              UUID semesterId, String semesterName, UUID programId, String programName,
                              UUID teacherId, String teacherName, int students) {}

    private static final String OPEN_OFFERINGS = """
            SELECT psc.id FROM program_semester_courses psc
            JOIN   semesters s ON s.id = psc.semester_id
            JOIN   programs  p ON p.id = s.program_id
            WHERE  s.status = 'OPEN' AND p.status = 'ACTIVE'
            """;

    private static final String OFFERING_COLUMNS = """
            SELECT psc.id, c.code, c.name AS course_name, c.credit_hours,
                   s.id AS semester_id, s.name AS semester_name,
                   p.id AS program_id, p.name AS program_name,
                   u.id AS teacher_id, u.name AS teacher_name,
                   (SELECT COUNT(*) FROM enrollments e
                    WHERE  e.psc_id = psc.id AND e.status = 'ACTIVE'
                    AND    e.course_role = 'STUDENT') AS students
            FROM   program_semester_courses psc
            JOIN   courses   c ON c.id = psc.course_id
            JOIN   semesters s ON s.id = psc.semester_id
            JOIN   programs  p ON p.id = s.program_id
            JOIN   users     u ON u.id = psc.teacher_id
            """;

    /** Every offering in the current term, in a stable order. */
    public List<OfferingRow> findOpenOfferings() {
        return jdbc.query(OFFERING_COLUMNS + """
                WHERE  s.status = 'OPEN' AND p.status = 'ACTIVE'
                ORDER  BY p.name, c.code
                """, Map.of(), (rs, i) -> offering(rs));
    }

    /**
     * Current-term offerings the user takes part in: enrolled as a student, or
     * running it by any of the ways {@link com.lms.shared.OfferingStaff} counts.
     */
    public List<OfferingRow> findOpenOfferingsFor(UUID userId) {
        return jdbc.query(OFFERING_COLUMNS + """
                WHERE  s.status = 'OPEN' AND p.status = 'ACTIVE'
                AND   (psc.teacher_id = :userId
                       OR EXISTS (SELECT 1 FROM course_assistants ca
                                  WHERE ca.psc_id = psc.id AND ca.user_id = :userId)
                       OR EXISTS (SELECT 1 FROM enrollments e
                                  WHERE e.psc_id = psc.id AND e.student_id = :userId
                                  AND   e.status = 'ACTIVE'))
                ORDER  BY c.code
                """, Map.of("userId", userId), (rs, i) -> offering(rs));
    }

    /**
     * Teachers who must be in the room besides the teacher of record: active
     * TEACHER course members. Assistants are left out — they are not needed at
     * every meeting, and counting them would block periods they could miss.
     */
    public Map<UUID, Set<UUID>> findCoTeachers() {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        jdbc.query("""
                SELECT e.psc_id, e.student_id FROM enrollments e
                WHERE  e.course_role = 'TEACHER' AND e.status = 'ACTIVE'
                AND    e.psc_id IN (""" + OPEN_OFFERINGS + ")",
                Map.of(), rs -> {
                    result.computeIfAbsent(uuid(rs.getString(1)), k -> new HashSet<>()).add(uuid(rs.getString(2)));
                });
        return result;
    }

    /** For each current-term offering, the other current-term offerings it shares a student with. */
    public Map<UUID, Set<UUID>> findSharedStudents() {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        jdbc.query("""
                WITH open_students AS (
                    SELECT e.psc_id, e.student_id FROM enrollments e
                    WHERE  e.status = 'ACTIVE' AND e.course_role = 'STUDENT'
                    AND    e.psc_id IN (""" + OPEN_OFFERINGS + """
                ))
                SELECT DISTINCT a.psc_id, b.psc_id
                FROM   open_students a
                JOIN   open_students b ON b.student_id = a.student_id AND b.psc_id > a.psc_id
                """, Map.of(), rs -> {
                    UUID a = uuid(rs.getString(1));
                    UUID b = uuid(rs.getString(2));
                    result.computeIfAbsent(a, k -> new HashSet<>()).add(b);
                    result.computeIfAbsent(b, k -> new HashSet<>()).add(a);
                });
        return result;
    }

    /** The offerings sharing at least one student with this one (any semester). */
    public Set<UUID> findOfferingsSharingStudents(UUID pscId) {
        return new HashSet<>(jdbc.query("""
                SELECT DISTINCT b.psc_id FROM enrollments a
                JOIN   enrollments b ON b.student_id = a.student_id AND b.psc_id <> a.psc_id
                WHERE  a.psc_id = :pscId
                AND    a.status = 'ACTIVE' AND a.course_role = 'STUDENT'
                AND    b.status = 'ACTIVE' AND b.course_role = 'STUDENT'
                """, Map.of("pscId", pscId), (rs, i) -> uuid(rs.getString(1))));
    }

    /** Cohorts with at least one member enrolled in each of the given offerings. */
    public Map<UUID, Set<UUID>> findCohorts(Collection<UUID> pscIds) {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        if (pscIds.isEmpty()) return result;
        jdbc.query("""
                SELECT DISTINCT e.psc_id, cm.cohort_id
                FROM   enrollments e
                JOIN   cohort_members cm ON cm.student_id = e.student_id
                WHERE  e.psc_id IN (:pscIds)
                AND    e.status = 'ACTIVE' AND e.course_role = 'STUDENT'
                """, Map.of("pscIds", pscIds), rs -> {
                    result.computeIfAbsent(uuid(rs.getString(1)), k -> new HashSet<>()).add(uuid(rs.getString(2)));
                });
        return result;
    }

    private static OfferingRow offering(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OfferingRow(
                uuid(rs.getString("id")), rs.getString("code"), rs.getString("course_name"),
                rs.getInt("credit_hours"),
                uuid(rs.getString("semester_id")), rs.getString("semester_name"),
                uuid(rs.getString("program_id")), rs.getString("program_name"),
                uuid(rs.getString("teacher_id")), rs.getString("teacher_name"),
                rs.getInt("students"));
    }

    private static UUID uuid(String s) {
        return UUID.fromString(s);
    }
}
