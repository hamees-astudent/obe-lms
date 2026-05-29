package com.lms.modules.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {

    List<AttendanceSession> findAllByPscIdOrderBySessionDateDesc(UUID pscId);

    List<AttendanceSession> findAllByPscIdAndSessionDate(UUID pscId, LocalDate sessionDate);

    /** Currently-open sessions for a course offering. */
    List<AttendanceSession> findAllByPscIdAndClosedAtIsNull(UUID pscId);

    /**
     * Fetches names and codes needed for {@link com.lms.shared.events.AttendanceAlertEvent}
     * without injecting cross-module repositories.
     */
    @Query(value = """
            SELECT
                u.email       AS studentEmail,
                u.name        AS studentName,
                c.code        AS courseCode,
                c.name        AS courseName,
                s.name        AS semesterName
            FROM program_semester_courses psc
            JOIN courses   c ON c.id = psc.course_id
            JOIN semesters s ON s.id = psc.semester_id
            CROSS JOIN (SELECT email, name FROM users WHERE id = :studentId) u
            WHERE psc.id = :pscId
            """, nativeQuery = true)
    Optional<AttendanceContextView> findAlertContext(
            @Param("pscId") UUID pscId,
            @Param("studentId") UUID studentId);
}
