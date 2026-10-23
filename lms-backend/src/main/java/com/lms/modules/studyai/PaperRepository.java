package com.lms.modules.studyai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.modules.studyai.dto.PaperItemResponse;
import com.lms.modules.studyai.dto.PaperRequest;
import com.lms.modules.studyai.dto.PaperResponse;
import com.lms.modules.studyai.dto.PrintSetResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

/** Question papers, their items and printed sets. */
@Repository
@RequiredArgsConstructor
class PaperRepository {

    private static final TypeReference<List<UUID>> UUID_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<UUID, List<String>>> OPTION_ORDERS = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    record PaperRow(UUID id, UUID pscId, String title, String instructions, Integer durationMinutes,
                    BigDecimal maxTotalMarks, BigDecimal totalMarks, String status) {}

    record ItemRow(UUID id, String section, int orderIndex, BigDecimal marks, GenQuestion question) {}

    record SetRow(String setCode, long seed, List<UUID> questionOrder, Map<UUID, List<String>> optionOrders,
                  boolean shuffledQuestions, boolean shuffledOptions, LocalDateTime printedAt) {}

    UUID create(UUID pscId, UUID createdBy, PaperRequest req) {
        return jdbc.queryForObject("""
                INSERT INTO question_papers (psc_id, created_by, title, instructions, duration_minutes, max_total_marks)
                VALUES (:psc, :by, :t, :i, :d, :m) RETURNING id
                """, new MapSqlParameterSource("psc", pscId).addValue("by", createdBy).addValue("t", req.title())
                        .addValue("i", req.instructions()).addValue("d", req.durationMinutes())
                        .addValue("m", req.maxTotalMarks()), UUID.class);
    }

    void update(UUID id, PaperRequest req) {
        jdbc.update("""
                UPDATE question_papers SET title = :t, instructions = :i, duration_minutes = :d, max_total_marks = :m
                WHERE id = :id
                """, new MapSqlParameterSource("id", id).addValue("t", req.title()).addValue("i", req.instructions())
                        .addValue("d", req.durationMinutes()).addValue("m", req.maxTotalMarks()));
    }

    void delete(UUID id) {
        jdbc.update("DELETE FROM question_papers WHERE id = :id", Map.of("id", id));
    }

    Optional<PaperRow> find(UUID id) {
        return jdbc.query("SELECT * FROM question_papers WHERE id = :id", Map.of("id", id), this::paperRow)
                .stream().findFirst();
    }

    List<PaperRow> list(UUID pscId) {
        return jdbc.query("SELECT * FROM question_papers WHERE psc_id = :psc ORDER BY created_at DESC",
                Map.of("psc", pscId), this::paperRow);
    }

    int itemCount(UUID paperId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM question_paper_items WHERE paper_id = :p",
                Map.of("p", paperId), Integer.class);
        return n == null ? 0 : n;
    }

    private PaperRow paperRow(ResultSet rs, int i) throws SQLException {
        return new PaperRow(rs.getObject("id", UUID.class), rs.getObject("psc_id", UUID.class), rs.getString("title"),
                rs.getString("instructions"), (Integer) rs.getObject("duration_minutes"),
                rs.getBigDecimal("max_total_marks"), rs.getBigDecimal("total_marks"), rs.getString("status"));
    }

    List<ItemRow> items(UUID paperId) {
        return jdbc.query("""
                SELECT id, section, order_index, marks, question::text AS question
                FROM   question_paper_items WHERE paper_id = :p
                ORDER  BY CASE section WHEN 'CHOICE' THEN 0 WHEN 'SHORT' THEN 1 ELSE 2 END, order_index
                """, Map.of("p", paperId),
                (rs, i) -> new ItemRow(rs.getObject("id", UUID.class), rs.getString("section"), rs.getInt("order_index"),
                        rs.getBigDecimal("marks"),
                        Sql.read(mapper, rs.getString("question"), GenQuestion.class, new GenQuestion())));
    }

    void addItem(UUID paperId, String section, int order, BigDecimal marks, GenQuestion q,
                 UUID sourceQuestionId, UUID sourceDraftId) {
        jdbc.update("""
                INSERT INTO question_paper_items (paper_id, section, order_index, question, marks,
                                                  source_question_id, source_draft_id)
                VALUES (:p, :s, :o, CAST(:q AS jsonb), :m, :sq, :sd)
                """, new MapSqlParameterSource("p", paperId).addValue("s", section).addValue("o", order)
                        .addValue("q", Sql.json(mapper, q)).addValue("m", marks)
                        .addValue("sq", sourceQuestionId).addValue("sd", sourceDraftId));
    }

    Optional<UUID> paperOfItem(UUID itemId) {
        return jdbc.queryForList("SELECT paper_id FROM question_paper_items WHERE id = :i", Map.of("i", itemId), UUID.class)
                .stream().findFirst();
    }

    void updateItemMarks(UUID itemId, BigDecimal marks, GenQuestion q) {
        jdbc.update("UPDATE question_paper_items SET marks = :m, question = CAST(:q AS jsonb) WHERE id = :i",
                new MapSqlParameterSource("i", itemId).addValue("m", marks).addValue("q", Sql.json(mapper, q)));
    }

    void deleteItem(UUID itemId) {
        jdbc.update("DELETE FROM question_paper_items WHERE id = :i", Map.of("i", itemId));
    }

    void setOrder(UUID itemId, int order) {
        jdbc.update("UPDATE question_paper_items SET order_index = :o WHERE id = :i",
                new MapSqlParameterSource("i", itemId).addValue("o", order));
    }

    /** Recomputes the cached total; editing a paper invalidates its printed sets. */
    void afterItemsChanged(UUID paperId) {
        jdbc.update("""
                UPDATE question_papers SET status = 'DRAFT',
                       total_marks = (SELECT COALESCE(SUM(marks), 0) FROM question_paper_items WHERE paper_id = :p)
                WHERE id = :p
                """, Map.of("p", paperId));
        jdbc.update("DELETE FROM paper_print_sets WHERE paper_id = :p", Map.of("p", paperId));
    }

    void replaceSets(UUID paperId, List<PaperShuffler.Layout> layouts, UUID printedBy) {
        jdbc.update("DELETE FROM paper_print_sets WHERE paper_id = :p", Map.of("p", paperId));
        for (PaperShuffler.Layout l : layouts) {
            jdbc.update("""
                    INSERT INTO paper_print_sets (paper_id, set_code, seed, question_order, option_orders,
                                                  shuffled_questions, shuffled_options, printed_by)
                    VALUES (:p, :c, :s, CAST(:qo AS jsonb), CAST(:oo AS jsonb), :sq, :so, :by)
                    """, new MapSqlParameterSource("p", paperId).addValue("c", l.setCode()).addValue("s", l.seed())
                            .addValue("qo", Sql.json(mapper, l.questionOrder()))
                            .addValue("oo", Sql.json(mapper, l.optionOrders()))
                            .addValue("sq", l.shuffledQuestions()).addValue("so", l.shuffledOptions())
                            .addValue("by", printedBy));
        }
        jdbc.update("UPDATE question_papers SET status = 'FINAL' WHERE id = :p", Map.of("p", paperId));
    }

    List<SetRow> sets(UUID paperId) {
        return jdbc.query("""
                SELECT set_code, seed, question_order::text AS qo, option_orders::text AS oo,
                       shuffled_questions, shuffled_options, printed_at
                FROM   paper_print_sets WHERE paper_id = :p ORDER BY set_code
                """, Map.of("p", paperId),
                (rs, i) -> new SetRow(rs.getString("set_code"), rs.getLong("seed"),
                        Sql.read(mapper, rs.getString("qo"), UUID_LIST, List.of()),
                        Sql.read(mapper, rs.getString("oo"), OPTION_ORDERS, Map.of()),
                        rs.getBoolean("shuffled_questions"), rs.getBoolean("shuffled_options"),
                        rs.getObject("printed_at", LocalDateTime.class)));
    }

    PaperResponse response(UUID paperId, int maxQuestions, boolean withItems) {
        PaperRow p = find(paperId).orElseThrow();
        List<PaperItemResponse> items = null;
        List<PrintSetResponse> sets = null;
        if (withItems) {
            items = items(paperId).stream().map(this::itemResponse).toList();
            sets = sets(paperId).stream().map(s -> new PrintSetResponse(s.setCode(), s.shuffledQuestions(),
                    s.shuffledOptions(), s.printedAt())).toList();
        }
        Map<String, Object> times = jdbc.queryForMap(
                "SELECT created_at, COALESCE(updated_at, created_at) AS updated_at FROM question_papers WHERE id = :id",
                Map.of("id", paperId));
        return new PaperResponse(p.id(), p.pscId(), p.title(), p.instructions(), p.durationMinutes(),
                p.maxTotalMarks(), p.totalMarks(), p.status(), itemCount(paperId), maxQuestions,
                toLocal(times.get("created_at")), toLocal(times.get("updated_at")), items, sets);
    }

    @SuppressWarnings("unchecked")
    private PaperItemResponse itemResponse(ItemRow r) {
        return new PaperItemResponse(r.id(), r.section(), r.orderIndex(), r.marks(),
                mapper.convertValue(r.question(), Map.class));
    }

    private static LocalDateTime toLocal(Object v) {
        return v instanceof java.sql.Timestamp t ? t.toLocalDateTime() : v instanceof LocalDateTime l ? l : null;
    }

    /** Quiz questions of an offering, as paper questions (plain SQL: they belong to assessment). */
    List<Map.Entry<UUID, GenQuestion>> quizQuestions(UUID pscId, Collection<UUID> questionIds) {
        if (questionIds.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT qq.id, qq.type, qq.question_text, qq.options::text AS options, qq.correct_answer::text AS correct,
                       qq.model_answer, qq.rubric::text AS rubric, qq.explanation, qq.marks, qq.bloom_level, qq.difficulty,
                       ARRAY(SELECT m.clo_id FROM quiz_question_clo_mappings m WHERE m.question_id = qq.id) AS clo_ids
                FROM   quiz_questions qq JOIN quizzes q ON q.id = qq.quiz_id
                WHERE  q.psc_id = :psc AND qq.id IN (:ids)
                ORDER  BY q.created_at, qq.order_index
                """, new MapSqlParameterSource("psc", pscId).addValue("ids", questionIds),
                (rs, i) -> Map.entry(rs.getObject("id", UUID.class), GenQuestion.builder()
                        .type(rs.getString("type")).questionText(rs.getString("question_text"))
                        .options(new ArrayList<>(Arrays.asList(Sql.read(mapper, rs.getString("options"),
                                GenQuestion.Option[].class, new GenQuestion.Option[0]))))
                        .correctAnswer(new ArrayList<>(Arrays.asList(Sql.read(mapper, rs.getString("correct"),
                                String[].class, new String[0]))))
                        .modelAnswer(rs.getString("model_answer"))
                        .rubric(new ArrayList<>(Arrays.asList(Sql.read(mapper, rs.getString("rubric"),
                                GenQuestion.RubricItem[].class, new GenQuestion.RubricItem[0]))))
                        .explanation(rs.getString("explanation")).marks(rs.getBigDecimal("marks"))
                        .bloomLevel(rs.getString("bloom_level")).difficulty(rs.getString("difficulty"))
                        .cloIds(new ArrayList<>(Sql.uuids(rs, "clo_ids"))).source("QUIZ")
                        .build()));
    }
}
