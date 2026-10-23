package com.lms.modules.studyai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

/** Students' practice attempts ({@code mock_attempts}) — never read by the transcript. */
@Repository
@RequiredArgsConstructor
class MockRepository {

    private static final TypeReference<List<GenQuestion>> QUESTIONS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Map<String, Object>>> RESULTS = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    record MockRow(UUID id, UUID studentId, UUID pscId, String kind, String status, String error,
                   Map<String, Object> config, List<GenQuestion> questions, Map<String, List<String>> answers,
                   Map<String, Map<String, Object>> results, BigDecimal score, BigDecimal totalMarks,
                   LocalDateTime createdAt, LocalDateTime submittedAt) {}

    UUID create(UUID studentId, UUID pscId, String kind, Map<String, Object> config) {
        return jdbc.queryForObject("""
                INSERT INTO mock_attempts (student_id, psc_id, kind, config) VALUES (:s, :p, :k, CAST(:c AS jsonb))
                RETURNING id
                """, new MapSqlParameterSource("s", studentId).addValue("p", pscId).addValue("k", kind)
                        .addValue("c", Sql.json(mapper, config)), UUID.class);
    }

    void ready(UUID id, List<GenQuestion> questions, BigDecimal total, List<String> notes) {
        jdbc.update("""
                UPDATE mock_attempts SET status = 'READY', questions = CAST(:q AS jsonb), total_marks = :t,
                       config = config || jsonb_build_object('notes', CAST(:n AS jsonb))
                WHERE id = :id
                """, new MapSqlParameterSource("id", id).addValue("q", Sql.json(mapper, questions))
                        .addValue("t", total).addValue("n", Sql.json(mapper, notes)));
    }

    void failed(UUID id, String error) {
        jdbc.update("UPDATE mock_attempts SET status = 'FAILED', error = :e WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("e", error));
    }

    void saveAnswers(UUID id, Map<String, List<String>> answers) {
        jdbc.update("UPDATE mock_attempts SET answers = CAST(:a AS jsonb) WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("a", Sql.json(mapper, answers)));
    }

    void graded(UUID id, Map<String, List<String>> answers, Map<String, Map<String, Object>> results, BigDecimal score) {
        jdbc.update("""
                UPDATE mock_attempts SET status = 'GRADED', answers = CAST(:a AS jsonb), results = CAST(:r AS jsonb),
                       score = :s, submitted_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource("id", id).addValue("a", Sql.json(mapper, answers))
                        .addValue("r", Sql.json(mapper, results)).addValue("s", score));
    }

    Optional<MockRow> find(UUID id) {
        return jdbc.query("""
                SELECT *, config::text AS cfg, questions::text AS qs, answers::text AS ans, results::text AS res
                FROM mock_attempts WHERE id = :id
                """, Map.of("id", id), this::row).stream().findFirst();
    }

    List<MockRow> list(UUID studentId, UUID pscId) {
        return jdbc.query("""
                SELECT *, config::text AS cfg, questions::text AS qs, answers::text AS ans, results::text AS res
                FROM   mock_attempts
                WHERE  student_id = :s AND (CAST(:p AS uuid) IS NULL OR psc_id = CAST(:p AS uuid))
                ORDER  BY created_at DESC LIMIT 50
                """, new MapSqlParameterSource("s", studentId).addValue("p", pscId == null ? null : pscId.toString()),
                this::row);
    }

    private MockRow row(ResultSet rs, int i) throws SQLException {
        return new MockRow(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getObject("psc_id", UUID.class), rs.getString("kind"), rs.getString("status"), rs.getString("error"),
                Sql.read(mapper, rs.getString("cfg"), Sql.MAP, new HashMap<>()),
                Sql.read(mapper, rs.getString("qs"), QUESTIONS, List.of()),
                Sql.read(mapper, rs.getString("ans"), Sql.ANSWERS, new HashMap<>()),
                Sql.read(mapper, rs.getString("res"), RESULTS, new HashMap<>()),
                rs.getBigDecimal("score"), rs.getBigDecimal("total_marks"),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("submitted_at", LocalDateTime.class));
    }
}
