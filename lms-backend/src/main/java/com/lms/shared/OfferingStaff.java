package com.lms.shared;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Who runs a course offering — the one rule every module checks writes against.
 *
 * <p>Staff are assigned three ways, and each counts: the offering's teacher of
 * record, a row in {@code course_assistants}, or an active TEACHER/ASSISTANT
 * course membership. Role alone is not enough: {@code hasRole('TEACHER')} lets
 * any teacher in the institution edit a course that is not theirs. Admins
 * always pass.
 *
 * <p>Plain SQL so no module takes an ORM dependency on another's tables. The
 * list queries that select "offerings this user runs"
 * ({@code ProgramSemesterCourseRepository.findTeachingOfferings},
 * {@code ExamDataRepository.findOfferingsForTeacher}) apply the same rule and
 * must be kept in step with it.
 */
@Component
@RequiredArgsConstructor
public class OfferingStaff {

    private static final String IS_STAFF_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM program_semester_courses psc
                WHERE  psc.id = :pscId AND psc.teacher_id = :userId
                UNION ALL
                SELECT 1 FROM course_assistants ca
                WHERE  ca.psc_id = :pscId AND ca.user_id = :userId
                UNION ALL
                SELECT 1 FROM enrollments e
                WHERE  e.psc_id = :pscId AND e.student_id = :userId
                AND    e.course_role IN ('TEACHER', 'ASSISTANT')
                AND    e.status = 'ACTIVE')
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /** Whether the user runs this offering (admins not considered). */
    public boolean isStaff(UUID pscId, UUID userId) {
        Boolean staff = jdbc.queryForObject(IS_STAFF_SQL,
                Map.of("pscId", pscId, "userId", userId), Boolean.class);
        return Boolean.TRUE.equals(staff);
    }

    /** Refuses with 403 unless the user is an admin or runs this offering. */
    public void require(UUID pscId, UUID userId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (!isStaff(pscId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not the teacher or an assistant of this course offering.");
        }
    }
}
