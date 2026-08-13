package com.lms.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    List<Quiz> findAllByPscIdOrderByCreatedAtDesc(UUID pscId);

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
            CROSS  JOIN quizzes q
            JOIN   program_semester_courses psc ON psc.id = q.psc_id
            JOIN   courses c                   ON c.id   = psc.course_id
            WHERE  q.id  = :quizId
            AND    u.id  = :studentId
            """, nativeQuery = true)
    AssessmentContextView findEventContext(
            @Param("quizId") UUID quizId,
            @Param("studentId") UUID studentId);

    /**
     * Whether the student holds an active STUDENT enrollment in the offering the
     * quiz belongs to. Native, for the same reason as above — the assessment
     * module does not depend on the enrollment module.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM   quizzes q
                JOIN   enrollments e ON e.psc_id = q.psc_id
                WHERE  q.id          = :quizId
                AND    e.student_id  = :studentId
                AND    e.status      = 'ACTIVE'
                AND    e.course_role = 'STUDENT')
            """, nativeQuery = true)
    boolean isStudentEnrolled(
            @Param("quizId") UUID quizId,
            @Param("studentId") UUID studentId);
}
