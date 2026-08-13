package com.lms.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    List<Assignment> findAllByPscIdOrderByDueDateAsc(UUID pscId);

    /**
     * Native query to enrich {@link com.lms.shared.events.AssessmentEvent} with student
     * and course details without injecting other module repositories.
     */
    @Query(value = """
            SELECT u.email      AS student_email,
                   u.name       AS student_name,
                   c.code       AS course_code,
                   c.name       AS course_name
            FROM   users u
            CROSS  JOIN assignments a
            JOIN   program_semester_courses psc ON psc.id = a.psc_id
            JOIN   courses c                   ON c.id   = psc.course_id
            WHERE  a.id  = :assignmentId
            AND    u.id  = :studentId
            """, nativeQuery = true)
    AssessmentContextView findEventContext(
            @Param("assignmentId") UUID assignmentId,
            @Param("studentId") UUID studentId);

    /**
     * Whether the student holds an active STUDENT enrollment in the offering the
     * assignment belongs to. Native, for the same reason as above — the
     * assessment module does not depend on the enrollment module.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM   assignments a
                JOIN   enrollments e ON e.psc_id = a.psc_id
                WHERE  a.id          = :assignmentId
                AND    e.student_id  = :studentId
                AND    e.status      = 'ACTIVE'
                AND    e.course_role = 'STUDENT')
            """, nativeQuery = true)
    boolean isStudentEnrolled(
            @Param("assignmentId") UUID assignmentId,
            @Param("studentId") UUID studentId);
}
