package com.lms.modules.courses;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgramSemesterCourseRepository extends JpaRepository<ProgramSemesterCourse, UUID> {

    List<ProgramSemesterCourse> findAllBySemesterId(UUID semesterId);

    List<ProgramSemesterCourse> findAllByCourse_Id(UUID courseId);

    List<ProgramSemesterCourse> findAllByTeacherId(UUID teacherId);

    boolean existsBySemesterIdAndCourse_Id(UUID semesterId, UUID courseId);

    Optional<ProgramSemesterCourse> findBySemesterIdAndCourse_Id(UUID semesterId, UUID courseId);

    /**
     * Offerings in open semesters of active programs that the user runs, by
     * any of the three ways staff are assigned: teacher of record,
     * {@code course_assistants}, or an active TEACHER/ASSISTANT course
     * membership. Same rule as {@link com.lms.shared.OfferingStaff} — keep them
     * in step.
     * Native, to avoid cross-module ORM joins to programs and enrollment.
     */
    @Query(value = """
            SELECT psc.id::text AS pscId,
                   s.name       AS semesterName,
                   p.name       AS programName
            FROM   program_semester_courses psc
            JOIN   semesters s ON s.id = psc.semester_id
            JOIN   programs  p ON p.id = s.program_id
            WHERE  s.status = 'OPEN'
            AND    p.status = 'ACTIVE'
            AND   (psc.teacher_id = :userId
                   OR EXISTS (SELECT 1 FROM course_assistants ca
                              WHERE ca.psc_id = psc.id AND ca.user_id = :userId)
                   OR EXISTS (SELECT 1 FROM enrollments e
                              WHERE e.psc_id = psc.id AND e.student_id = :userId
                              AND   e.course_role IN ('TEACHER', 'ASSISTANT')
                              AND   e.status = 'ACTIVE'))
            """, nativeQuery = true)
    List<TeachingOfferingView> findTeachingOfferings(@Param("userId") UUID userId);

    /** Whether the user holds an active course membership (any role) in the offering. */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM enrollments
                WHERE  psc_id = :pscId AND student_id = :userId AND status = 'ACTIVE')
            """, nativeQuery = true)
    boolean isActiveMember(@Param("pscId") UUID pscId, @Param("userId") UUID userId);
}
