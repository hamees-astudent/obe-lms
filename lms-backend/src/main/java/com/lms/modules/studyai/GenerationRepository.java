package com.lms.modules.studyai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.modules.studyai.GenQuestion.CloScore;
import com.lms.modules.studyai.GenQuestion.Option;
import com.lms.modules.studyai.GenQuestion.RubricItem;
import com.lms.modules.studyai.dto.CitationResponse;
import com.lms.modules.studyai.dto.GenerationJobResponse;
import com.lms.modules.studyai.dto.QuestionDraftResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** Generation jobs and their drafts ({@code question_generation_jobs}, {@code generated_question_drafts}). */
@Repository
@RequiredArgsConstructor
class GenerationRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RetrievalService retrieval;

    record JobRow(UUID id, UUID pscId, UUID requestedBy, String status, Map<String, Object> config,
                  List<UUID> materialIds, List<UUID> topicIds) {}

    record DraftRow(UUID id, UUID jobId, UUID pscId, GenQuestion question, String status) {}

    UUID createJob(UUID pscId, UUID requestedBy, Map<String, Object> config, List<UUID> materialIds,
                   List<UUID> topicIds) {
        return jdbc.queryForObject("""
                INSERT INTO question_generation_jobs (psc_id, requested_by, config, material_ids, topic_ids)
                VALUES (:psc, :by, CAST(:cfg AS jsonb), CAST(:m AS uuid[]), CAST(:t AS uuid[])) RETURNING id
                """, new MapSqlParameterSource("psc", pscId).addValue("by", requestedBy)
                        .addValue("cfg", Sql.json(mapper, config)).addValue("m", Sql.uuidArray(materialIds))
                        .addValue("t", Sql.uuidArray(topicIds)), UUID.class);
    }

    Optional<JobRow> findJob(UUID id) {
        return jdbc.query("""
                SELECT id, psc_id, requested_by, status, config::text AS config, material_ids, topic_ids
                FROM   question_generation_jobs WHERE id = :id
                """, Map.of("id", id),
                (rs, i) -> new JobRow(rs.getObject("id", UUID.class), rs.getObject("psc_id", UUID.class),
                        rs.getObject("requested_by", UUID.class), rs.getString("status"),
                        Sql.read(mapper, rs.getString("config"), Sql.MAP, new HashMap<>()),
                        Sql.uuids(rs, "material_ids"), Sql.uuids(rs, "topic_ids")))
                .stream().findFirst();
    }

    void markRunning(UUID id) {
        jdbc.update("UPDATE question_generation_jobs SET status = 'RUNNING' WHERE id = :id", Map.of("id", id));
    }

    void markFailed(UUID id, String error) {
        jdbc.update("""
                UPDATE question_generation_jobs SET status = 'FAILED', error = :e, finished_at = now() WHERE id = :id
                """, new MapSqlParameterSource("id", id).addValue("e", error));
    }

    @Transactional
    void complete(UUID id, List<GenQuestion> questions, String mode, List<String> notes) {
        int i = 0;
        for (GenQuestion q : questions) insertDraft(id, i++, q);
        jdbc.update("""
                UPDATE question_generation_jobs
                SET    status = 'READY', mode = :mode, finished_at = now(),
                       config = config || jsonb_build_object('notes', CAST(:notes AS jsonb))
                WHERE  id = :id
                """, new MapSqlParameterSource("id", id).addValue("mode", mode)
                        .addValue("notes", Sql.json(mapper, notes)));
    }

    UUID insertDraft(UUID jobId, int order, GenQuestion q) {
        return jdbc.queryForObject("""
                INSERT INTO generated_question_drafts
                    (job_id, order_index, type, question_text, options, correct_answer, model_answer, rubric,
                     explanation, marks, bloom_level, difficulty, suggested_clo_ids, clo_scores, clo_rationale,
                     bloom_source, source, source_chunk_ids)
                VALUES (:job, :o, :type, :text, CAST(:opts AS jsonb), CAST(:correct AS jsonb), :model,
                        CAST(:rubric AS jsonb), :expl, :marks, :bloom, :diff, CAST(:clos AS uuid[]),
                        CAST(:scores AS jsonb), :rat, :bsrc, :src, CAST(:chunks AS uuid[]))
                RETURNING id
                """, params(q).addValue("job", jobId).addValue("o", order), UUID.class);
    }

    void replaceDraft(UUID draftId, GenQuestion q, String status, boolean edited) {
        jdbc.update("""
                UPDATE generated_question_drafts SET
                    type = :type, question_text = :text, options = CAST(:opts AS jsonb),
                    correct_answer = CAST(:correct AS jsonb), model_answer = :model, rubric = CAST(:rubric AS jsonb),
                    explanation = :expl, marks = :marks, bloom_level = :bloom, difficulty = :diff,
                    suggested_clo_ids = CAST(:clos AS uuid[]), clo_scores = CAST(:scores AS jsonb),
                    clo_rationale = :rat, bloom_source = :bsrc, source = :src,
                    source_chunk_ids = CAST(:chunks AS uuid[]), status = :status, edited = :edited
                WHERE id = :id
                """, params(q).addValue("id", draftId).addValue("status", status).addValue("edited", edited));
    }

    void setStatus(Collection<UUID> draftIds, String status) {
        if (draftIds.isEmpty()) return;
        jdbc.update("UPDATE generated_question_drafts SET status = :s WHERE id IN (:ids)",
                new MapSqlParameterSource("s", status).addValue("ids", draftIds));
    }

    private MapSqlParameterSource params(GenQuestion q) {
        return new MapSqlParameterSource()
                .addValue("type", q.getType()).addValue("text", q.getQuestionText())
                .addValue("opts", Sql.json(mapper, q.getOptions())).addValue("correct", Sql.json(mapper, q.getCorrectAnswer()))
                .addValue("model", q.getModelAnswer()).addValue("rubric", Sql.json(mapper, q.getRubric()))
                .addValue("expl", q.getExplanation()).addValue("marks", q.getMarks())
                .addValue("bloom", q.getBloomLevel()).addValue("diff", q.getDifficulty())
                .addValue("clos", Sql.uuidArray(q.getCloIds())).addValue("scores", Sql.json(mapper, q.getCloScores()))
                .addValue("rat", q.getCloRationale()).addValue("bsrc", q.getBloomSource())
                .addValue("src", q.getSource() == null ? "TEMPLATE" : q.getSource())
                .addValue("chunks", Sql.uuidArray(q.getSourceChunkIds()));
    }

    List<GenerationJobResponse> listJobs(UUID pscId) {
        return jdbc.query("""
                SELECT j.id, j.psc_id, j.status, j.mode, j.error, j.config::text AS config, j.created_at, j.finished_at,
                       COUNT(d.id) AS drafts, COALESCE(SUM(d.marks) FILTER (WHERE d.status <> 'REJECTED'), 0) AS total
                FROM   question_generation_jobs j
                LEFT JOIN generated_question_drafts d ON d.job_id = j.id
                WHERE  j.psc_id = :psc
                GROUP  BY j.id ORDER BY j.created_at DESC LIMIT 30
                """, Map.of("psc", pscId), (rs, i) -> job(rs, null));
    }

    GenerationJobResponse job(UUID id) {
        List<QuestionDraftResponse> drafts = drafts(id);
        return jdbc.query("""
                SELECT j.id, j.psc_id, j.status, j.mode, j.error, j.config::text AS config, j.created_at, j.finished_at,
                       (SELECT COUNT(*) FROM generated_question_drafts d WHERE d.job_id = j.id) AS drafts,
                       (SELECT COALESCE(SUM(marks), 0) FROM generated_question_drafts d
                        WHERE d.job_id = j.id AND d.status <> 'REJECTED') AS total
                FROM   question_generation_jobs j WHERE j.id = :id
                """, Map.of("id", id), (rs, i) -> job(rs, drafts)).get(0);
    }

    private GenerationJobResponse job(java.sql.ResultSet rs, List<QuestionDraftResponse> drafts) throws java.sql.SQLException {
        Map<String, Object> config = Sql.read(mapper, rs.getString("config"), Sql.MAP, new HashMap<>());
        Object notes = config.remove("notes");
        List<String> noteList = new ArrayList<>();
        if (notes instanceof List<?> l) l.forEach(n -> noteList.add(String.valueOf(n)));
        return new GenerationJobResponse(rs.getObject("id", UUID.class), rs.getObject("psc_id", UUID.class),
                rs.getString("status"), rs.getString("mode"), rs.getString("error"), config, noteList,
                rs.getBigDecimal("total"), rs.getInt("drafts"), rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("finished_at", LocalDateTime.class), drafts);
    }

    List<QuestionDraftResponse> drafts(UUID jobId) {
        List<DraftRow> rows = draftRows("d.job_id = :id", Map.of("id", jobId));
        Map<UUID, CitationResponse> cites = citations(rows);
        return rows.stream().map(r -> toResponse(r, cites)).toList();
    }

    Optional<DraftRow> findDraft(UUID id) {
        return draftRows("d.id = :id", Map.of("id", id)).stream().findFirst();
    }

    List<DraftRow> findDrafts(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return draftRows("d.id IN (:ids)", Map.of("ids", ids));
    }

    QuestionDraftResponse draftResponse(UUID id) {
        DraftRow row = findDraft(id).orElseThrow();
        return toResponse(row, citations(List.of(row)));
    }

    private List<DraftRow> draftRows(String where, Map<String, ?> params) {
        return jdbc.query("""
                SELECT d.*, d.options::text AS options_json, d.correct_answer::text AS correct_json,
                       d.rubric::text AS rubric_json, d.clo_scores::text AS scores_json, j.psc_id
                FROM   generated_question_drafts d JOIN question_generation_jobs j ON j.id = d.job_id
                WHERE\s""" + where + " ORDER BY d.order_index", params,
                (rs, i) -> {
                    GenQuestion q = GenQuestion.builder()
                            .id(rs.getObject("id", UUID.class).toString())
                            .type(rs.getString("type")).questionText(rs.getString("question_text"))
                            .options(new ArrayList<>(Arrays.asList(Sql.read(mapper, rs.getString("options_json"), Option[].class, new Option[0]))))
                            .correctAnswer(new ArrayList<>(Arrays.asList(Sql.read(mapper, rs.getString("correct_json"), String[].class, new String[0]))))
                            .modelAnswer(rs.getString("model_answer"))
                            .rubric(new ArrayList<>(Arrays.asList(Sql.read(mapper, rs.getString("rubric_json"), RubricItem[].class, new RubricItem[0]))))
                            .explanation(rs.getString("explanation")).marks(rs.getBigDecimal("marks"))
                            .bloomLevel(rs.getString("bloom_level")).bloomSource(rs.getString("bloom_source"))
                            .difficulty(rs.getString("difficulty"))
                            .cloIds(new ArrayList<>(Sql.uuids(rs, "suggested_clo_ids")))
                            .cloScores(new ArrayList<>(Arrays.asList(Sql.read(mapper, rs.getString("scores_json"), CloScore[].class, new CloScore[0]))))
                            .cloRationale(rs.getString("clo_rationale")).source(rs.getString("source"))
                            .sourceChunkIds(new ArrayList<>(Sql.uuids(rs, "source_chunk_ids")))
                            .build();
                    q.setEditedFlag(rs.getBoolean("edited"));
                    q.setOrderIndex(rs.getInt("order_index"));
                    return new DraftRow(rs.getObject("id", UUID.class), rs.getObject("job_id", UUID.class),
                            rs.getObject("psc_id", UUID.class), q, rs.getString("status"));
                });
    }

    /** Where each draft's source passages are, for "from Slide 12" links. */
    private Map<UUID, CitationResponse> citations(List<DraftRow> rows) {
        Set<UUID> ids = new HashSet<>();
        rows.forEach(r -> ids.addAll(r.question().getSourceChunkIds()));
        return retrieval.locate(ids);
    }

    private QuestionDraftResponse toResponse(DraftRow r, Map<UUID, CitationResponse> cites) {
        GenQuestion q = r.question();
        List<CitationResponse> sources = q.getSourceChunkIds().stream().map(cites::get).filter(Objects::nonNull)
                .limit(3).toList();
        return new QuestionDraftResponse(r.id(), q.getOrderIndex(), q.getType(), q.getQuestionText(),
                maps(q.getOptions()), q.getCorrectAnswer(), q.getModelAnswer(), maps(q.getRubric()),
                q.getExplanation(), q.getMarks(), q.getBloomLevel(), q.getBloomSource(), q.getDifficulty(),
                q.getCloIds(), maps(q.getCloScores()), q.getCloRationale(), q.getSource(), sources,
                r.status(), q.isEditedFlag());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(List<?> items) {
        return items.stream().map(i -> (Map<String, Object>) mapper.convertValue(i, Map.class)).toList();
    }

    BigDecimal totalOf(Collection<DraftRow> rows) {
        return rows.stream().map(r -> r.question().getMarks()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
