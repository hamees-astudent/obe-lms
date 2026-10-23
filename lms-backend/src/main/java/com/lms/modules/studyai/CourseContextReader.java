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
 * Read-only facts about a course offering that other modules own: its course,
 * its CLOs, and the quiz questions currently open to a student. Plain SQL, so
 * this module takes no dependency on the courses or assessment entities.
 */
@Component
@RequiredArgsConstructor
class CourseContextReader {

    private final NamedParameterJdbcTemplate jdbc;

    record Offering(UUID pscId, UUID courseId, String courseCode, String courseName) {}

    record CloRow(UUID id, String code, String title, String description, String bloomLevel) {
        String text() {
            return description == null || description.isBlank() ? title : title + ". " + description;
        }
    }

    Offering offering(UUID pscId) {
        return jdbc.query("""
                SELECT psc.id, c.id AS course_id, c.code, c.name
                FROM   program_semester_courses psc JOIN courses c ON c.id = psc.course_id
                WHERE  psc.id = :psc
                """, Map.of("psc", pscId),
                (rs, i) -> new Offering(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class),
                        rs.getString("code"), rs.getString("name")))
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course offering not found"));
    }

    List<CloRow> clos(UUID courseId) {
        return jdbc.query("""
                SELECT id, code, title, description, bloom_level FROM clos
                WHERE  course_id = :course ORDER BY order_index
                """, Map.of("course", courseId),
                (rs, i) -> new CloRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("title"),
                        rs.getString("description"), rs.getString("bloom_level")));
    }

    record OpenQuizQuestion(String quizTitle, String questionText) {}

    /**
     * Questions of quizzes the student can sit right now and has not
     * submitted — the ones the assistant must not answer for them.
     */
    List<OpenQuizQuestion> openQuizQuestions(UUID pscId, UUID studentId) {
        return jdbc.query("""
                SELECT q.title, qq.question_text
                FROM   quizzes q
                JOIN   quiz_questions qq ON qq.quiz_id = q.id
                WHERE  q.psc_id = :psc
                AND   (q.available_from  IS NULL OR q.available_from  <= now())
                AND   (q.available_until IS NULL OR q.available_until >= now())
                AND    NOT EXISTS (SELECT 1 FROM quiz_submissions s
                                   WHERE s.quiz_id = q.id AND s.student_id = :sid
                                   AND   s.submitted_at IS NOT NULL)
                """, new MapSqlParameterSource("psc", pscId).addValue("sid", studentId),
                (rs, i) -> new OpenQuizQuestion(rs.getString("title"), rs.getString("question_text")));
    }
}
