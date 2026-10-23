package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.BloomClassifier;
import com.lms.infrastructure.nlp.BloomLevel;
import com.lms.infrastructure.nlp.TextAnalyzer;
import com.lms.infrastructure.nlp.TfIdf;
import com.lms.modules.studyai.CourseContextReader.CloRow;
import com.lms.modules.studyai.GenQuestion.CloScore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Suggests which CLO a question assesses.
 *
 * <p>Each CLO is scored by the TF-IDF cosine similarity of its statement to
 * the question (with its answer or options, which carry most of the subject
 * terms), raised when the CLO is written at the question's Bloom level — a CLO
 * to "design" a schema is a poorer home for a recall question than one to
 * "describe" it, when they share vocabulary. The raise is a multiplier, so it
 * separates related CLOs but cannot make an unrelated one win. The level of a CLO without one set is read from its
 * opening verb.
 *
 * <p>When the local model proposed CLO codes, its choice is taken if it is
 * among the three best-scoring; otherwise the top score wins. The scores of
 * every candidate are kept so the teacher can see how close the call was.
 */
@Component
@RequiredArgsConstructor
class CloSuggester {

    /** Multipliers, so a level match can break a tie but never rescue a CLO on another topic. */
    static final double SAME_LEVEL_BONUS = 0.3;
    static final double ADJACENT_LEVEL_BONUS = 0.15;

    private final TextAnalyzer analyzer;
    private final BloomClassifier bloom;

    record Suggestion(List<UUID> cloIds, List<CloScore> scores, String rationale) {}

    Suggestion suggest(String questionText, String answerText, String bloomLevel, List<CloRow> clos,
                       Collection<String> modelCodes) {
        if (clos.isEmpty()) return new Suggestion(List.of(), List.of(), "This course has no CLOs defined.");

        List<String> qTerms = new ArrayList<>(analyzer.terms(questionText));
        if (answerText != null) qTerms.addAll(analyzer.terms(answerText));
        List<List<String>> cloTerms = clos.stream().map(c -> analyzer.terms(c.text())).toList();

        List<List<String>> corpus = new ArrayList<>(cloTerms);
        corpus.add(qTerms);
        TfIdf model = TfIdf.fit(corpus);
        Map<String, Double> qv = model.vector(qTerms);
        Optional<BloomLevel> qLevel = BloomLevel.parse(bloomLevel);

        List<CloScore> scores = new ArrayList<>();
        for (int i = 0; i < clos.size(); i++) {
            CloRow clo = clos.get(i);
            double s = TfIdf.cosine(qv, model.vector(cloTerms.get(i)));
            Optional<BloomLevel> cl = BloomLevel.parse(clo.bloomLevel()).or(() -> bloom.classifyOutcome(clo.title()));
            if (qLevel.isPresent() && cl.isPresent()) {
                int gap = Math.abs(qLevel.get().rank() - cl.get().rank());
                if (gap == 0) s *= 1 + SAME_LEVEL_BONUS;
                else if (gap == 1) s *= 1 + ADJACENT_LEVEL_BONUS;
            }
            scores.add(new CloScore(clo.id(), clo.code(), Math.round(s * 1000) / 1000.0));
        }
        List<CloScore> ranked = scores.stream().sorted(Comparator.comparingDouble(CloScore::score).reversed()).toList();

        CloScore chosen = ranked.get(0);
        String how = "highest similarity";
        if (modelCodes != null && !modelCodes.isEmpty()) {
            Set<String> wanted = new HashSet<>();
            modelCodes.forEach(c -> wanted.add(normaliseCode(c)));
            for (CloScore s : ranked.subList(0, Math.min(3, ranked.size()))) {
                if (wanted.contains(normaliseCode(s.code()))) {
                    chosen = s;
                    how = s == ranked.get(0) ? "highest similarity, also the model's choice"
                            : "the model's choice, among the top three by similarity";
                    break;
                }
            }
        }
        UUID pick = chosen.cloId();
        CloRow clo = clos.stream().filter(c -> c.id().equals(pick)).findFirst().orElseThrow();
        return new Suggestion(List.of(clo.id()), ranked, rationale(clo, questionText, answerText, bloomLevel, how));
    }

    /** "Shares 'normal form', 'redundancy' with CLO-2; both at Apply level (highest similarity)." */
    private String rationale(CloRow clo, String question, String answer, String level, String how) {
        Set<String> cloStems = analyzer.termSet(clo.text());
        LinkedHashSet<String> shared = new LinkedHashSet<>();
        String text = question + " " + (answer == null ? "" : answer);
        for (String token : analyzer.tokens(text)) {
            String w = token.toLowerCase(Locale.ROOT);
            if (w.length() > 3 && cloStems.contains(analyzer.stem(w))) shared.add(w);
            if (shared.size() >= 4) break;
        }
        StringBuilder sb = new StringBuilder();
        if (shared.isEmpty()) sb.append("Closest match to ").append(clo.code());
        else sb.append("Shares \"").append(String.join("\", \"", shared)).append("\" with ").append(clo.code());
        Optional<BloomLevel> cl = BloomLevel.parse(clo.bloomLevel()).or(() -> bloom.classifyOutcome(clo.title()));
        if (level != null && cl.isPresent() && cl.get().name().equals(level)) {
            sb.append("; both at ").append(capitalise(level)).append(" level");
        }
        return sb.append(" (").append(how).append(").").toString();
    }

    private static String normaliseCode(String code) {
        return code == null ? "" : code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String capitalise(String level) {
        return level.charAt(0) + level.substring(1).toLowerCase(Locale.ROOT);
    }
}
