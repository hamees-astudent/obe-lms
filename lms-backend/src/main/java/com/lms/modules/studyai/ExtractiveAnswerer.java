package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.TextAnalyzer;
import com.lms.infrastructure.nlp.TfIdf;
import com.lms.modules.studyai.RetrievalService.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Answers a question by quoting the most relevant sentences of the retrieved
 * passages — the assistant's answer when the local model is not available.
 *
 * <p>Every sentence of the passages is scored by how many of the question's
 * terms it contains, each weighted by its rarity among those sentences
 * (TF-IDF), plus a bonus for coming from a higher-ranked passage. The best
 * few are quoted in document order, each with its source marker. It cannot
 * paraphrase or combine, but it cannot invent anything either.
 */
@Component
@RequiredArgsConstructor
class ExtractiveAnswerer {

    static final int MAX_SENTENCES = 4;

    private final TextAnalyzer analyzer;

    record Candidate(int source, int position, String text, List<String> terms, double chunkRank) {}

    /** The answer text with [S#] markers, or empty when no sentence matches the question. */
    Optional<String> answer(String question, List<RetrievedChunk> chunks) {
        List<String> queryTerms = analyzer.terms(question);
        if (queryTerms.isEmpty() || chunks.isEmpty()) return Optional.empty();

        List<Candidate> candidates = new ArrayList<>();
        double topRank = chunks.stream().mapToDouble(RetrievedChunk::rank).max().orElse(1);
        for (int s = 0; s < chunks.size(); s++) {
            RetrievedChunk c = chunks.get(s);
            List<String> sentences = analyzer.sentences(c.content());
            for (int p = 0; p < sentences.size(); p++) {
                String text = sentences.get(p);
                int words = Chunker.wordCount(text);
                if (words < 4 || words > 80) continue;
                candidates.add(new Candidate(s, p, text, analyzer.terms(text),
                        topRank <= 0 ? 0 : c.rank() / topRank));
            }
        }
        if (candidates.isEmpty()) return Optional.empty();

        TfIdf model = TfIdf.fit(candidates.stream().map(Candidate::terms).toList());
        Map<String, Double> q = model.vector(queryTerms);
        Set<String> qSet = new HashSet<>(queryTerms);

        record Scored(Candidate c, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (Candidate c : candidates) {
            long overlap = c.terms().stream().distinct().filter(qSet::contains).count();
            if (overlap == 0) continue;
            double cos = TfIdf.cosine(q, model.vector(c.terms()));
            double coverage = (double) overlap / qSet.size();
            scored.add(new Scored(c, cos + 0.5 * coverage + 0.15 * c.chunkRank()));
        }
        if (scored.isEmpty()) return Optional.empty();

        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<Candidate> best = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Scored s : scored) {
            if (best.size() >= MAX_SENTENCES) break;
            if (s.score() < scored.get(0).score() * 0.35) break;
            String key = s.c().text().toLowerCase(Locale.ROOT);
            if (seen.add(key)) best.add(s.c());
        }
        best.sort(Comparator.comparingInt(Candidate::source).thenComparingInt(Candidate::position));

        StringBuilder sb = new StringBuilder("Here is what your course material says about this:\n\n");
        for (Candidate c : best) {
            sb.append("- ").append(c.text().strip()).append(" [S").append(c.source() + 1).append("]\n");
        }
        return Optional.of(sb.toString().strip());
    }
}
