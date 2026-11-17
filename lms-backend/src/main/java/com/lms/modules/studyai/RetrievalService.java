package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.Stopwords;
import com.lms.modules.studyai.dto.CitationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Finds the course-material passages relevant to a question, with PostgreSQL
 * full-text search.
 *
 * <p>Each passage's {@code tsv} column holds its stemmed terms (English
 * configuration), headings weighted above body text.
 *
 * <ol>
 *   <li>The question is reduced to what it is about: instruction words
 *       ("describe", "explain", "tell me about", "in detail") are dropped, so
 *       "describe applications of agents in real-world scenarios" and
 *       "explain agents in a real-world scenario" search for the same
 *       thing.</li>
 *   <li>Passages matching <em>any</em> of the remaining stemmed terms are
 *       candidates. Each is scored by how much of the question it covers, every
 *       term weighted by its rarity in the offering (a passage that mentions
 *       "agent" beats one that mentions "world" five times), plus a smaller
 *       share of cover-density rank ({@code ts_rank_cd}).</li>
 *   <li>Front matter, reference lists and course-admin slides are pushed down
 *       ({@link InstructionalFilter}).</li>
 *   <li>The result is diversified (a few passages per material at most) and
 *       cut to a token budget so it fits the local model's context window.</li>
 * </ol>
 *
 * <p>The best passage's coverage is returned with the passages, so whether a
 * question is answerable is decided here, by one deterministic measure, and
 * not by how a model happens to react to the wording.
 */
@Service
@RequiredArgsConstructor
public class RetrievalService {

    static final int CANDIDATES = 60;
    static final int PER_MATERIAL = 3;
    /** Share of the score that comes from cover-density rank rather than term coverage. */
    static final double RANK_WEIGHT = 0.3;
    /** Score multiplier for front matter and course administration. */
    static final double NON_INSTRUCTIONAL_PENALTY = 0.3;
    /** Most passages read when sampling a selection for question generation. */
    static final int GENERATION_SCAN_LIMIT = 6000;

    /**
     * Words that say what to do with a topic rather than what the topic is.
     * Matched on whole words, case-insensitively; PostgreSQL's English
     * configuration already drops ordinary stop words.
     */
    static final Set<String> INSTRUCTION_WORDS = Set.of(
            "describe", "describes", "description", "explain", "explains", "explanation", "discuss", "discussion",
            "elaborate", "define", "definition", "tell", "give", "provide", "list", "state", "mention", "outline",
            "brief", "briefly", "detail", "details", "detailed", "overview", "summary", "summarize", "summarise",
            "illustrate", "example", "examples", "concept", "concepts", "notion", "meaning", "mean", "means",
            "understand", "understanding", "please", "help", "know", "show", "simple", "simply", "short", "words",
            "terms", "kindly", "write", "note", "notes", "elaboration", "clarify", "clearly", "work", "works");

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

    /** Passages and how well the best of them covers the question (0–1). */
    public record Search(List<RetrievedChunk> chunks, double coverage) {
        static final Search EMPTY = new Search(List.of(), 0);
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
        return searchScored(pscIds, question, tokenBudget, materialIds).chunks();
    }

    /** {@link #search}, with the coverage of the best passage. */
    public Search searchScored(Collection<UUID> pscIds, String question, int tokenBudget,
                               Collection<UUID> materialIds) {
        if (pscIds.isEmpty() || question == null || question.isBlank()) return Search.EMPTY;
        String q = focus(question.length() > 1000 ? question.substring(0, 1000) : question);

        List<String> all = jdbc.queryForList(
                "SELECT DISTINCT lexeme FROM unnest(to_tsvector('english', :q))", Map.of("q", q), String.class);
        // "real-world" yields 'real-world', 'real' and 'world'; count the parts only, so a hyphen
        // in the question neither triples the term's weight nor misses "real world" in the text.
        List<String> lexemes = all.stream().filter(l -> !l.contains("-")).toList();
        if (lexemes.isEmpty()) lexemes = all;
        if (lexemes.isEmpty()) return Search.EMPTY;

        // How many passages of these offerings contain each term: rare terms say more about relevance.
        boolean filter = materialIds != null && !materialIds.isEmpty();
        MapSqlParameterSource p = new MapSqlParameterSource("pscIds", Sql.uuidArray(pscIds))
                .addValue("lexemes", Sql.textArray(lexemes))
                .addValue("materialIds", Sql.uuidArray(filter ? materialIds : List.of()));
        String scope = """
                FROM   material_chunks c
                JOIN   course_materials m ON m.id = c.material_id
                WHERE  c.psc_id = ANY(CAST(:pscIds AS uuid[]))
                AND    m.visible
                """ + (filter ? " AND c.material_id = ANY(CAST(:materialIds AS uuid[]))\n" : "");
        Map<String, Integer> df = new HashMap<>();
        int[] total = {0};
        jdbc.query("WITH scoped AS (SELECT c.tsv " + scope + """
                )
                SELECT l.lexeme,
                       (SELECT COUNT(*) FROM scoped s WHERE s.tsv @@ to_tsquery('simple', quote_literal(l.lexeme))) AS df,
                       (SELECT COUNT(*) FROM scoped) AS total
                FROM   unnest(CAST(:lexemes AS text[])) AS l(lexeme)
                """, p, rs -> {
                    total[0] = rs.getInt("total");
                    if (rs.getInt("df") > 0) df.put(rs.getString("lexeme"), rs.getInt("df"));
                });
        if (df.isEmpty() || total[0] == 0) return Search.EMPTY;

        // Inverse document frequency. A term the material never uses counts as the rarest
        // possible: "explain quantum tunnelling" is not answered because a slide says "explain".
        Map<String, Double> weight = new HashMap<>();
        double absent = Math.log(1.0 + total[0]);
        for (String l : lexemes) {
            Integer n = df.get(l);
            weight.put(l, n == null ? absent : Math.log(1.0 + (double) total[0] / n));
        }
        double weightSum = weight.values().stream().mapToDouble(Double::doubleValue).sum();

        String orq = String.join(" | ", df.keySet().stream().map(RetrievalService::quoteLexeme).toList());
        List<Candidate> candidates = jdbc.query(SELECT + """
                       ts_rank_cd(c.tsv, tq, 32) AS rank,
                       ARRAY(SELECT t FROM unnest(tsvector_to_array(c.tsv)) t
                             WHERE t = ANY(CAST(:lexemes AS text[]))) AS matched
                """ + scope.replace("WHERE  c.psc_id", "CROSS  JOIN to_tsquery('simple', :orq) AS tq\n                WHERE  c.psc_id")
                + """
                AND    c.tsv @@ tq
                ORDER  BY rank DESC, c.chunk_index
                LIMIT  %d
                """.formatted(CANDIDATES * 4), p.addValue("orq", orq),
                (rs, i) -> new Candidate(ROW.mapRow(rs, i), Sql.texts(rs, "matched")));
        if (candidates.isEmpty()) return Search.EMPTY;

        double topRank = candidates.stream().mapToDouble(c -> c.chunk().rank()).max().orElse(1);
        List<RetrievedChunk> scored = new ArrayList<>();
        double best = 0;
        for (Candidate c : candidates) {
            double covered = c.matched().stream().distinct().mapToDouble(l -> weight.getOrDefault(l, 0.0)).sum();
            double coverage = weightSum <= 0 ? 0 : covered / weightSum;
            double score = (1 - RANK_WEIGHT) * coverage + RANK_WEIGHT * (topRank <= 0 ? 0 : c.chunk().rank() / topRank);
            if (InstructionalFilter.isNonInstructional(c.chunk())) {
                score *= NON_INSTRUCTIONAL_PENALTY;
            } else {
                best = Math.max(best, coverage);
            }
            scored.add(withRank(c.chunk(), score));
        }
        scored.sort(Comparator.comparingDouble(RetrievedChunk::rank).reversed()
                .thenComparingInt(RetrievedChunk::chunkIndex));
        List<RetrievedChunk> top = scored.subList(0, Math.min(CANDIDATES, scored.size()));
        return new Search(select(top, tokenBudget), best);
    }

    private record Candidate(RetrievedChunk chunk, List<String> matched) {}

    /**
     * The topic of a question without its instruction words: "Describe the
     * applications of agents in detail" → "the applications of agents".
     * Falls back to the question as asked when nothing would be left.
     */
    static String focus(String question) {
        String[] words = question.split("(?<=[^\\p{L}\\p{N}'-])|(?=[^\\p{L}\\p{N}'-])");
        StringBuilder out = new StringBuilder();
        int kept = 0;
        for (String w : words) {
            if (INSTRUCTION_WORDS.contains(w.toLowerCase(Locale.ROOT))) continue;
            if (!w.isBlank() && Character.isLetterOrDigit(w.charAt(0))
                    && !Stopwords.contains(w.toLowerCase(Locale.ROOT))) kept++;
            out.append(w);
        }
        return kept == 0 ? question : out.toString().replaceAll("\\s+", " ").strip();
    }

    private static String quoteLexeme(String lexeme) {
        return "'" + lexeme.replace("'", "''").replace("\\", "\\\\") + "'";
    }

    private static RetrievedChunk withRank(RetrievedChunk c, double rank) {
        return new RetrievedChunk(c.id(), c.materialId(), c.materialTitle(), c.chunkIndex(), c.locationType(),
                c.locationStart(), c.locationEnd(), c.heading(), c.content(), c.keyPhrases(), c.tokenEstimate(), rank);
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
        return forGeneration(pscId, topicIds, materialIds, visibleOnly, limit, 0);
    }

    /**
     * As above, but without front matter (preface, contents, references,
     * course-admin slides), and spread over the whole selection: when it holds
     * more than {@code limit} passages, every n-th one is taken, starting at a
     * point chosen by {@code seed}. Taking the first {@code limit} instead
     * would ask every practice paper about the first chapter.
     *
     * @throws ResponseStatusException 422 when the selection holds only front matter
     */
    public List<RetrievedChunk> forGeneration(UUID pscId, Collection<UUID> topicIds, Collection<UUID> materialIds,
                                              boolean visibleOnly, int limit, long seed) {
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
        List<RetrievedChunk> all = jdbc.query(SELECT + """
                       1.0 AS rank
                FROM   material_chunks c
                JOIN   course_materials m ON m.id = c.material_id
                WHERE  c.psc_id = :psc AND (m.visible OR NOT :visibleOnly) AND
                """ + where + " ORDER BY m.order_index, m.created_at, c.chunk_index LIMIT " + GENERATION_SCAN_LIMIT,
                p, ROW);
        List<RetrievedChunk> content = all.stream().filter(c -> !InstructionalFilter.isNonInstructional(c)).toList();
        if (content.isEmpty() && !all.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The selected material is only front matter (preface, contents, references or course "
                    + "information). Choose the chapters or lectures that teach the subject.");
        }
        return spread(content, limit, seed);
    }

    /** Every n-th passage, so {@code limit} passages cover the whole list; in document order. */
    static List<RetrievedChunk> spread(List<RetrievedChunk> chunks, int limit, long seed) {
        if (chunks.size() <= limit) return chunks;
        double step = chunks.size() / (double) limit;
        double offset = Math.floorMod(seed, 1000) / 1000.0 * step;
        List<RetrievedChunk> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            int at = (int) Math.min(chunks.size() - 1, Math.floor(offset + i * step));
            out.add(chunks.get(at));
        }
        return out;
    }

    /** Where passages are — material and page/slide — with the passage text, for citing them. */
    public Map<UUID, CitationResponse> locate(Collection<UUID> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) return Map.of();
        Map<UUID, CitationResponse> out = new HashMap<>();
        jdbc.query("""
                SELECT c.id, c.material_id, m.title, c.location_type, c.location_start, c.location_end, c.heading,
                       c.content
                FROM   material_chunks c JOIN course_materials m ON m.id = c.material_id
                WHERE  c.id IN (:ids)
                """, Map.of("ids", chunkIds), rs -> {
                    RetrievedChunk c = new RetrievedChunk(rs.getObject("id", UUID.class),
                            rs.getObject("material_id", UUID.class), rs.getString("title"), 0,
                            rs.getString("location_type"), rs.getInt("location_start"), rs.getInt("location_end"),
                            rs.getString("heading"), rs.getString("content"), List.of(), 0, 0);
                    out.put(c.id(), Citations.citation(0, c));
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
