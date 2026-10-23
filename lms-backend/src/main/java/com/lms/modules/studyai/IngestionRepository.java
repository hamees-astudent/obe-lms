package com.lms.modules.studyai;

import com.lms.modules.studyai.Chunker.Chunk;
import com.lms.modules.studyai.dto.IngestionStatusResponse;
import com.lms.modules.studyai.dto.TopicResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** Ingestion state, chunks and topics ({@code material_ingestions}, {@code material_chunks}, {@code material_topics}). */
@Repository
@RequiredArgsConstructor
class IngestionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** What ingestion needs to know about a material. */
    record MaterialRow(UUID id, UUID pscId, String type, String title, String objectKey,
                       String fileName, String mimeType, boolean visible) {}

    record Topic(String name, String summary, int start, int end) {}

    Optional<MaterialRow> findMaterial(UUID materialId) {
        return jdbc.query("""
                SELECT id, psc_id, type, title, visible,
                       content->>'objectKey' AS object_key,
                       content->>'fileName'  AS file_name,
                       content->>'mimeType'  AS mime_type
                FROM   course_materials WHERE id = :id
                """, Map.of("id", materialId),
                (rs, i) -> new MaterialRow(rs.getObject("id", UUID.class), rs.getObject("psc_id", UUID.class),
                        rs.getString("type"), rs.getString("title"), rs.getString("object_key"),
                        rs.getString("file_name"), rs.getString("mime_type"), rs.getBoolean("visible")))
                .stream().findFirst();
    }

    Optional<String> findSha(UUID materialId) {
        return jdbc.query("SELECT content_sha256 FROM material_ingestions WHERE material_id = :id AND status = 'READY'",
                Map.of("id", materialId), (rs, i) -> rs.getString(1)).stream().filter(Objects::nonNull).findFirst();
    }

    void markPending(UUID materialId) {
        jdbc.update("""
                INSERT INTO material_ingestions (material_id, status) VALUES (:id, 'PENDING')
                ON CONFLICT (material_id) DO UPDATE SET status = 'PENDING', error = NULL
                """, Map.of("id", materialId));
    }

    void markProcessing(UUID materialId) {
        jdbc.update("""
                INSERT INTO material_ingestions (material_id, status, started_at, attempts)
                VALUES (:id, 'PROCESSING', now(), 1)
                ON CONFLICT (material_id) DO UPDATE
                SET status = 'PROCESSING', error = NULL, started_at = now(),
                    attempts = material_ingestions.attempts + 1
                """, Map.of("id", materialId));
    }

    void markFinished(UUID materialId, String status, String error) {
        jdbc.update("""
                INSERT INTO material_ingestions (material_id, status, error, finished_at)
                VALUES (:id, :status, :error, now())
                ON CONFLICT (material_id) DO UPDATE
                SET status = :status, error = :error, finished_at = now()
                """, new MapSqlParameterSource("id", materialId).addValue("status", status)
                        .addValue("error", error));
    }

    /** Replaces the material's chunks and topics and marks it READY, all at once. */
    @Transactional
    void replaceIndex(UUID materialId, UUID pscId, List<Chunk> chunks, List<Topic> topics,
                      int pageCount, String sha) {
        Map<String, Object> id = Map.of("id", materialId);
        jdbc.update("DELETE FROM material_chunks WHERE material_id = :id", id);
        jdbc.update("DELETE FROM material_topics WHERE material_id = :id", id);

        SqlParameterSource[] chunkRows = chunks.stream().map(c -> new MapSqlParameterSource()
                .addValue("m", materialId).addValue("psc", pscId).addValue("i", c.index())
                .addValue("lt", c.locationType()).addValue("ls", c.start()).addValue("le", c.end())
                .addValue("h", c.heading()).addValue("c", c.content())
                .addValue("kp", Sql.textArray(c.keyPhrases())).addValue("t", c.tokenEstimate()))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO material_chunks (material_id, psc_id, chunk_index, location_type, location_start,
                                             location_end, heading, content, key_phrases, token_estimate)
                VALUES (:m, :psc, :i, :lt, :ls, :le, :h, :c, CAST(:kp AS text[]), :t)
                """, chunkRows);

        List<SqlParameterSource> topicRows = new ArrayList<>();
        for (int i = 0; i < topics.size(); i++) {
            Topic t = topics.get(i);
            topicRows.add(new MapSqlParameterSource().addValue("m", materialId).addValue("psc", pscId)
                    .addValue("n", truncate(t.name(), 200)).addValue("s", t.summary())
                    .addValue("ls", t.start()).addValue("le", t.end()).addValue("o", i));
        }
        jdbc.batchUpdate("""
                INSERT INTO material_topics (material_id, psc_id, name, summary, location_start, location_end, order_index)
                VALUES (:m, :psc, :n, :s, :ls, :le, :o)
                """, topicRows.toArray(SqlParameterSource[]::new));

        jdbc.update("""
                INSERT INTO material_ingestions (material_id, status, page_count, chunk_count, content_sha256, finished_at)
                VALUES (:id, 'READY', :pages, :chunks, :sha, now())
                ON CONFLICT (material_id) DO UPDATE
                SET status = 'READY', error = NULL, page_count = :pages, chunk_count = :chunks,
                    content_sha256 = :sha, finished_at = now()
                """, new MapSqlParameterSource("id", materialId).addValue("pages", pageCount)
                        .addValue("chunks", chunks.size()).addValue("sha", sha));
    }

    void renameTopics(UUID materialId, Map<Integer, String[]> namesByOrder) {
        namesByOrder.forEach((order, nameAndSummary) -> jdbc.update("""
                UPDATE material_topics SET name = :n, summary = COALESCE(:s, summary)
                WHERE material_id = :m AND order_index = :o
                """, new MapSqlParameterSource("m", materialId).addValue("o", order)
                        .addValue("n", truncate(nameAndSummary[0], 200)).addValue("s", nameAndSummary[1])));
    }

    /** Every DOCUMENT material of the offering with its index state. */
    List<IngestionStatusResponse> statusForOffering(UUID pscId) {
        return jdbc.query("""
                SELECT m.id, m.title, m.visible,
                       COALESCE(i.status, 'PENDING') AS status, i.error, i.page_count, i.chunk_count,
                       i.finished_at,
                       (SELECT COUNT(*) FROM material_topics t WHERE t.material_id = m.id) AS topic_count
                FROM   course_materials m
                LEFT JOIN material_ingestions i ON i.material_id = m.id
                WHERE  m.psc_id = :psc AND m.type = 'DOCUMENT'
                ORDER  BY m.order_index, m.created_at
                """, Map.of("psc", pscId),
                (rs, n) -> new IngestionStatusResponse(rs.getObject("id", UUID.class), rs.getString("title"),
                        rs.getString("status"), rs.getString("error"), (Integer) rs.getObject("page_count"),
                        (Integer) rs.getObject("chunk_count"), rs.getInt("topic_count"), rs.getBoolean("visible"),
                        rs.getObject("finished_at", LocalDateTime.class)));
    }

    /** Topics of the offering, optionally only from visible materials. */
    List<TopicResponse> topics(UUID pscId, boolean visibleOnly) {
        return jdbc.query("""
                SELECT t.id, t.material_id, m.title AS material_title, t.name, t.summary,
                       t.location_start, t.location_end,
                       (SELECT location_type FROM material_chunks c WHERE c.material_id = t.material_id LIMIT 1)
                           AS location_type
                FROM   material_topics t
                JOIN   course_materials m ON m.id = t.material_id
                JOIN   material_ingestions i ON i.material_id = t.material_id AND i.status = 'READY'
                WHERE  t.psc_id = :psc AND (m.visible OR NOT :visibleOnly)
                ORDER  BY m.order_index, m.created_at, t.order_index
                """, new MapSqlParameterSource("psc", pscId).addValue("visibleOnly", visibleOnly),
                (rs, n) -> new TopicResponse(rs.getObject("id", UUID.class), rs.getObject("material_id", UUID.class),
                        rs.getString("material_title"), rs.getString("name"), rs.getString("summary"),
                        rs.getString("location_type"), rs.getInt("location_start"), rs.getInt("location_end")));
    }

    /** DOCUMENT materials of an offering whose index is missing (for a bulk reindex). */
    List<UUID> unindexedDocuments(UUID pscId) {
        return jdbc.queryForList("""
                SELECT m.id FROM course_materials m
                LEFT JOIN material_ingestions i ON i.material_id = m.id
                WHERE  m.psc_id = :psc AND m.type = 'DOCUMENT'
                AND    (i.material_id IS NULL OR i.status IN ('PENDING', 'FAILED'))
                """, Map.of("psc", pscId), UUID.class);
    }

    /** Ingestions left PROCESSING by a restart, to retry. */
    List<UUID> stuck() {
        return jdbc.queryForList("""
                SELECT material_id FROM material_ingestions
                WHERE  status = 'PENDING'
                OR    (status = 'PROCESSING' AND started_at < now() - INTERVAL '30 minutes')
                """, Map.of(), UUID.class);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
