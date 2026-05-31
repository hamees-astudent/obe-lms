package com.lms.modules.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    Optional<Enrollment> findByPscIdAndStudentId(UUID pscId, UUID studentId);

    List<Enrollment> findAllByStudentId(UUID studentId);

    List<Enrollment> findAllByStudentIdAndStatus(UUID studentId, String status);

    List<Enrollment> findAllByPscId(UUID pscId);

    List<Enrollment> findAllByPscIdAndStatus(UUID pscId, String status);

    long countByPscIdAndStatus(UUID pscId, String status);

    // ── Cross-module native queries (no repository injection needed) ──────────

    /**
     * Returns the max_capacity of a program_semester_course.
     * Used for capacity enforcement without injecting the courses module repository.
     */
    @Query(value = "SELECT max_capacity FROM program_semester_courses WHERE id = :pscId",
           nativeQuery = true)
    Optional<Integer> findMaxCapacityByPscId(@Param("pscId") UUID pscId);

    /**
     * Enriches an EnrollmentEvent by joining PSC → course → semester → program
     * and fetching the student's name/email — all in one native query.
     */
    @Query(value = """
            SELECT
                u.email      AS studentEmail,
                u.name       AS studentName,
                c.code       AS courseCode,
                c.name       AS courseName,
                s.name       AS semesterName,
                p.name       AS programName
            FROM program_semester_courses psc
            JOIN courses   c ON c.id  = psc.course_id
            JOIN semesters s ON s.id  = psc.semester_id
            JOIN programs  p ON p.id  = s.program_id
            CROSS JOIN (SELECT email, name FROM users WHERE id = :studentId) u
            WHERE psc.id = :pscId
            """,
           nativeQuery = true)
    Optional<EnrollmentContextView> findEventContext(
            @Param("pscId") UUID pscId,
            @Param("studentId") UUID studentId);

    /**
     * Fetches (studentId, studentName) pairs for every enrollment in a course offering.
     * Used to populate {@link com.lms.modules.enrollment.dto.EnrollmentResponse#studentName}
     * without requiring cross-module repository injection.
     */
    @Query(value = """
            SELECT e.student_id::text AS studentId, u.name AS studentName
            FROM enrollments e
            JOIN users u ON u.id = e.student_id
            WHERE e.psc_id = :pscId
            """, nativeQuery = true)
    List<StudentNameView> findStudentNamesByPscId(@Param("pscId") UUID pscId);
}
