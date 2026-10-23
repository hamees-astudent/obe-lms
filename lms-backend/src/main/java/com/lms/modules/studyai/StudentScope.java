package com.lms.modules.studyai;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which course offerings a student may use the study assistant for: those
 * they hold an active STUDENT enrollment in. Every chat and mock request is
 * checked here, and retrieval only ever searches these offerings, so a student
 * can never get answers out of another course's material.
 */
@Component
@RequiredArgsConstructor
class StudentScope {

    private final NamedParameterJdbcTemplate jdbc;

    record EnrolledOffering(UUID pscId, String courseCode, String courseName, String semesterName,
                            int readyMaterials) {}

    List<EnrolledOffering> offerings(UUID studentId) {
        return jdbc.query("""
                SELECT psc.id, c.code, c.name, s.name AS semester_name,
                       (SELECT COUNT(*) FROM course_materials m
                        JOIN material_ingestions i ON i.material_id = m.id AND i.status = 'READY'
                        WHERE m.psc_id = psc.id AND m.visible) AS ready
                FROM   enrollments e
                JOIN   program_semester_courses psc ON psc.id = e.psc_id
                JOIN   courses c   ON c.id = psc.course_id
                JOIN   semesters s ON s.id = psc.semester_id
                WHERE  e.student_id = :sid AND e.status = 'ACTIVE' AND e.course_role = 'STUDENT'
                ORDER  BY c.code
                """, Map.of("sid", studentId),
                (rs, i) -> new EnrolledOffering(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getString("semester_name"), rs.getInt("ready")));
    }

    boolean isEnrolled(UUID pscId, UUID studentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM enrollments
                               WHERE psc_id = :psc AND student_id = :sid
                               AND status = 'ACTIVE' AND course_role = 'STUDENT')
                """, new MapSqlParameterSource("psc", pscId).addValue("sid", studentId), Boolean.class));
    }

    void require(UUID pscId, UUID studentId) {
        if (!isEnrolled(pscId, studentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The study assistant only covers courses you are enrolled in.");
        }
    }
}
