package com.lms.modules.studyai;

import com.lms.modules.studyai.dto.CitationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Finds the course-material passages relevant to a question, with PostgreSQL
 * full-text search.
 *
 * <p>Each passage's {@code tsv} column holds its stemmed terms (English
 * configuration), headings weighted above body text. A question is parsed with
 * {@code websearch_to_tsquery}, which accepts free text without syntax errors
 * — quotes become phrases, everything else is AND-ed — and passages are
 * ranked by cover density ({@code ts_rank_cd}), which rewards the question's
 * terms appearing close together.
 *
 * <p>An AND of every term often matches nothing for a conversational question,
 * so a second pass ORs the question's stemmed terms instead. The result is then
 * diversified (a few passages per material at most) and cut to a token budget
 * so it fits the local model's context window.
 */
@Service
@RequiredArgsConstructor
public class RetrievalService {

    static final int CANDIDATES = 40;
    static final int PER_MATERIAL = 3;

    private final NamedParameterJdbcTemplate jdbc;

    public record RetrievedChunk(UUID id, UUID materialId, String materialTitle, int chunkIndex,
                                 String locationType, int locationStart, int locationEnd,
                                 String heading, String content, List<String> keyPhrases,
                                 int tokenEstimate, double rank) {

        /** "Slide 12", "Pages 4–5", "Section 3". */
        public String locationLabel() {
            String unit = switch (locationType) {
                case "SLIDE" -> "Slide";
                case "PAGE" -> "Page";
                default -> "Section";
            };
            return locationStart == locationEnd
                    ? unit + " " + locationStart
                    : unit + "s " + locationStart + "–" + locationEnd;
        }
    }

    private static final String SELECT = """
            SELECT c.id, c.material_id, m.title AS material_title, c.chunk_index, c.location_type,
                   c.location_start, c.location_end, c.heading, c.content, c.key_phrases, c.token_estimate,
            """;

    private static final RowMapper<RetrievedChunk> ROW = (rs, i) -> new RetrievedChunk(
            rs.getObject("id", UUID.class), rs.getObject("material_id", UUID.class),
            rs.getString("material_title"), rs.getInt("chunk_index"), rs.getString("location_type"),
            rs.getInt("location_start"), rs.getInt("location_end"), rs.getString("heading"),
            rs.getString("content"), Sql.texts(rs, "key_phrases"), rs.getInt("token_estimate"),
            rs.getDouble("rank"));

    /**
     * The best passages for {@code question} among the visible material of the
     * given offerings, at most {@code tokenBudget} tokens in all.
     *
     * @param materialIds restrict to these materials, or null for all
     */
    public List<RetrievedChunk> search(Collection<UUID> pscIds, String question, int tokenBudget,
                                       Collection<UUID> materialIds) {
        if (pscIds.isEmpty() || question == null || question.isBlank()) return List.of();
        String q = question.length() > 1000 ? question.substring(0, 1000) : question;

        List<RetrievedChunk> hits = query("websearch_to_tsquery('english', :q)", pscIds, q, materialIds);
        if (hits.isEmpty()) {
            String any = jdbc.queryForObject("""
                    SELECT string_agg(quote_literal(lexeme), ' | ')
                    FROM   unnest(to_tsvector('english', :q))
                    """, Map.of("q", q), String.class);
            if (any != null && !any.isBlank()) {
                hits = query("to_tsquery('simple', :orq)", pscIds, any, materialIds);
            }
        }
        return select(hits, tokenBudget);
    }

    private List<RetrievedChunk> query(String tsquery, Collection<UUID> pscIds, String q,
                                       Collection<UUID> materialIds) {
        boolean filter = materialIds != null && !materialIds.isEmpty();
        String sql = SELECT + """
                       ts_rank_cd(c.tsv, tq, 32) AS rank
                FROM   material_chunks c
                JOIN   course_materials m ON m.id = c.material_id
                CROSS  JOIN %s AS tq
                WHERE  c.psc_id = ANY(CAST(:pscIds AS uuid[]))
                AND    m.visible
                AND    c.tsv @@ tq
                """.formatted(tsquery)
                + (filter ? " AND c.material_id = ANY(CAST(:materialIds AS uuid[]))" : "")
                + " ORDER BY rank DESC, c.chunk_index LIMIT " + CANDIDATES;
        MapSqlParameterSource p = new MapSqlParameterSource("pscIds", Sql.uuidArray(pscIds))
                .addValue("q", q).addValue("orq", q)
                .addValue("materialIds", Sql.uuidArray(filter ? materialIds : List.of()));
        return jdbc.query(sql, p, ROW);
    }

    /** At most {@link #PER_MATERIAL} passages per material, best first, within the budget. */
    static List<RetrievedChunk> select(List<RetrievedChunk> ranked, int tokenBudget) {
        Map<UUID, Integer> perMaterial = new HashMap<>();
        List<RetrievedChunk> out = new ArrayList<>();
        int used = 0;
        for (RetrievedChunk c : ranked) {
            if (perMaterial.getOrDefault(c.materialId(), 0) >= PER_MATERIAL) continue;
            int cost = Math.max(1, c.tokenEstimate());
            if (used + cost > tokenBudget && !out.isEmpty()) continue;
            out.add(c);
            used += cost;
            perMaterial.merge(c.materialId(), 1, Integer::sum);
        }
        return out;
    }

    /**
     * Passages for question generation: those inside the chosen topics' page
     * ranges, or all of the chosen materials, or the whole offering — in
     * document order. Hidden material is included for teachers, who generate
     * questions on next week's lecture before publishing it.
     */
    public List<RetrievedChunk> forGeneration(UUID pscId, Collection<UUID> topicIds, Collection<UUID> materialIds,
                                              boolean visibleOnly, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("psc", pscId)
                .addValue("topics", Sql.uuidArray(topicIds == null ? List.of() : topicIds))
                .addValue("materials", Sql.uuidArray(materialIds == null ? List.of() : materialIds))
                .addValue("visibleOnly", visibleOnly);
        String where;
        if (topicIds != null && !topicIds.isEmpty()) {
            where = """
                    EXISTS (SELECT 1 FROM material_topics t
                            WHERE t.id = ANY(CAST(:topics AS uuid[]))
                            AND   t.material_id = c.material_id
                            AND   c.location_start <= t.location_end
                            AND   c.location_end   >= t.location_start)
                    """;
        } else if (materialIds != null && !materialIds.isEmpty()) {
            where = "c.material_id = ANY(CAST(:materials AS uuid[]))";
        } else {
            where = "TRUE";
        }
        return jdbc.query(SELECT + """
                       1.0 AS rank
                FROM   material_chunks c
                JOIN   course_materials m ON m.id = c.material_id
                WHERE  c.psc_id = :psc AND (m.visible OR NOT :visibleOnly) AND
                """ + where + " ORDER BY m.order_index, m.created_at, c.chunk_index LIMIT " + limit, p, ROW);
    }

    /** Where passages are — material and page/slide — for citing them. */
    public Map<UUID, CitationResponse> locate(Collection<UUID> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) return Map.of();
        Map<UUID, CitationResponse> out = new HashMap<>();
        jdbc.query("""
                SELECT c.id, c.material_id, m.title, c.location_type, c.location_start, c.location_end, c.heading
                FROM   material_chunks c JOIN course_materials m ON m.id = c.material_id
                WHERE  c.id IN (:ids)
                """, Map.of("ids", chunkIds), rs -> {
                    RetrievedChunk c = new RetrievedChunk(rs.getObject("id", UUID.class),
                            rs.getObject("material_id", UUID.class), rs.getString("title"), 0,
                            rs.getString("location_type"), rs.getInt("location_start"), rs.getInt("location_end"),
                            rs.getString("heading"), "", List.of(), 0, 0);
                    out.put(c.id(), new CitationResponse(null, c.id(), c.materialId(), c.materialTitle(),
                            c.locationType(), c.locationStart(), c.locationEnd(), c.locationLabel(), c.heading()));
                });
        return out;
    }

    /** Key phrases across a whole offering — the pool MCQ distractors are drawn from. */
    public List<String> phrasePool(UUID pscId, int limit) {
        return jdbc.queryForList("""
                SELECT phrase FROM (
                    SELECT unnest(key_phrases) AS phrase FROM material_chunks WHERE psc_id = :psc) p
                GROUP  BY phrase ORDER BY COUNT(*) DESC, phrase LIMIT :limit
                """, new MapSqlParameterSource("psc", pscId).addValue("limit", limit), String.class);
    }
}
