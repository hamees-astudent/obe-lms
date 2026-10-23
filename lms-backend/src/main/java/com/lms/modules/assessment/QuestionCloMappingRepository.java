package com.lms.modules.assessment;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Question-level CLO mappings ({@code quiz_question_clo_mappings}).
 *
 * <p>Plain SQL: the CLO belongs to the courses module, so it is referenced by
 * id only, as every other cross-module mapping table is.
 */
@Repository
@RequiredArgsConstructor
class QuestionCloMappingRepository {

    private final NamedParameterJdbcTemplate jdbc;

    Map<UUID, List<UUID>> findByQuestionIds(Collection<UUID> questionIds) {
        if (questionIds.isEmpty()) return Map.of();
        Map<UUID, List<UUID>> out = new HashMap<>();
        jdbc.query("""
                SELECT question_id, clo_id FROM quiz_question_clo_mappings
                WHERE  question_id IN (:ids) ORDER BY created_at
                """, Map.of("ids", questionIds),
                rs -> {
                    out.computeIfAbsent(rs.getObject("question_id", UUID.class), k -> new ArrayList<>())
                       .add(rs.getObject("clo_id", UUID.class));
                });
        return out;
    }

    void replace(UUID questionId, List<UUID> cloIds) {
        jdbc.update("DELETE FROM quiz_question_clo_mappings WHERE question_id = :q", Map.of("q", questionId));
        for (UUID cloId : new LinkedHashSet<>(cloIds)) {
            jdbc.update("INSERT INTO quiz_question_clo_mappings (question_id, clo_id) VALUES (:q, :c)",
                    Map.of("q", questionId, "c", cloId));
        }
    }

    /**
     * CLO ids among {@code cloIds} that belong to the course of an offering.
     * Takes the offering rather than the quiz: a quiz created in the same
     * transaction is not flushed yet, so a join through {@code quizzes} would
     * not find it.
     */
    Set<UUID> cloIdsOfOfferingCourse(UUID pscId, Collection<UUID> cloIds) {
        if (cloIds.isEmpty()) return Set.of();
        return new HashSet<>(jdbc.queryForList("""
                SELECT c.id FROM clos c
                JOIN   program_semester_courses psc ON psc.course_id = c.course_id
                WHERE  psc.id = :pscId AND c.id IN (:cloIds)
                """, new MapSqlParameterSource("pscId", pscId).addValue("cloIds", cloIds), UUID.class));
    }

    /**
     * Adds a quiz-level CLO mapping for every CLO its questions assess that the
     * quiz is not mapped to yet, weighted by the share of the quiz's marks
     * those questions carry. The transcript reads quiz-level mappings, so a
     * question mapping alone would never reach attainment.
     *
     * <p>Existing quiz-level rows are left alone: the teacher may have set
     * those weights by hand.
     */
    void addMissingQuizMappings(UUID quizId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT m.clo_id, SUM(qq.marks) AS mapped_marks, MAX(q.total_marks) AS total_marks
                FROM   quiz_question_clo_mappings m
                JOIN   quiz_questions qq ON qq.id = m.question_id
                JOIN   quizzes q ON q.id = qq.quiz_id
                WHERE  qq.quiz_id = :quizId
                AND    NOT EXISTS (SELECT 1 FROM quiz_clo_mappings x
                                   WHERE x.quiz_id = :quizId AND x.clo_id = m.clo_id)
                GROUP  BY m.clo_id
                """, Map.of("quizId", quizId));
        for (Map<String, Object> row : rows) {
            BigDecimal mapped = (BigDecimal) row.get("mapped_marks");
            BigDecimal total = (BigDecimal) row.get("total_marks");
            BigDecimal weight = total == null || total.signum() == 0
                    ? BigDecimal.valueOf(100)
                    : mapped.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP)
                            .max(BigDecimal.ONE).min(BigDecimal.valueOf(100));
            jdbc.update("INSERT INTO quiz_clo_mappings (quiz_id, clo_id, weight) VALUES (:q, :c, :w)",
                    Map.of("q", quizId, "c", row.get("clo_id"), "w", weight));
        }
    }
}
