package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.TextAnalyzer;
import com.lms.infrastructure.nlp.TfIdf;
import com.lms.modules.studyai.GenQuestion.Option;
import com.lms.modules.studyai.RetrievalService.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Rejects generated questions a teacher should never be shown, whoever wrote
 * them: malformed choice questions, written questions with no usable model
 * answer, answers the source material does not support, and near-duplicates
 * of a question already accepted.
 *
 * <p>The grounding check is what keeps a small local model honest: the correct
 * option (or model answer) must share most of its content terms with the
 * passages the question was written from, so a plausible-sounding fact the
 * model made up is dropped rather than printed on a paper.
 */
@Component
@RequiredArgsConstructor
class QuestionValidator {

    static final double DUPLICATE_SIMILARITY = 0.85;
    static final double CHOICE_GROUNDING = 0.5;
    static final double WRITTEN_GROUNDING = 0.4;

    private final TextAnalyzer analyzer;

    /** The question cleaned up (option ids assigned), or why it was rejected. */
    record Result(GenQuestion question, String rejection) {
        boolean ok() { return rejection == null; }
    }

    Result validate(GenQuestion q, List<RetrievedChunk> sources, List<GenQuestion> accepted) {
        String text = q.getQuestionText() == null ? "" : q.getQuestionText().strip();
        if (text.length() < 10 || text.length() > 1200) return reject(q, "question text length");

        GenQuestion cleaned = q.toBuilder().questionText(text).build();
        Set<String> sourceTerms = new HashSet<>();
        for (RetrievedChunk c : sources) sourceTerms.addAll(analyzer.terms(c.content()));

        if (q.isWritten()) {
            String model = q.getModelAnswer() == null ? "" : q.getModelAnswer().strip();
            if (Chunker.wordCount(model) < 5) return reject(q, "model answer too short");
            if (!sourceTerms.isEmpty() && grounding(model, sourceTerms) < WRITTEN_GROUNDING) {
                return reject(q, "model answer not supported by the material");
            }
            cleaned.setOptions(new ArrayList<>());
            cleaned.setCorrectAnswer(new ArrayList<>());
            cleaned.setModelAnswer(model);
        } else {
            List<Option> options = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            Map<String, String> idMap = new HashMap<>();
            for (Option o : q.getOptions()) {
                String t = o.text() == null ? "" : o.text().strip();
                if (t.isEmpty() || t.length() > 300) return reject(q, "empty or overlong option");
                if (!seen.add(t.toLowerCase(Locale.ROOT).replaceAll("\\W+", ""))) return reject(q, "duplicate options");
                String id = String.valueOf((char) ('a' + options.size()));
                idMap.put(o.id(), id);
                options.add(new Option(id, t));
            }
            if (options.size() < 3 || options.size() > 6) return reject(q, "needs 3-6 options");
            List<String> correct = q.getCorrectAnswer().stream().map(idMap::get).filter(Objects::nonNull)
                    .distinct().toList();
            if ("MCQ".equals(q.getType()) && correct.size() != 1) return reject(q, "MCQ needs exactly one correct option");
            if ("MSQ".equals(q.getType()) && (correct.size() < 2 || correct.size() >= options.size())) {
                return reject(q, "MSQ needs at least two correct options and one wrong one");
            }
            if (!sourceTerms.isEmpty()) {
                String correctText = String.join(" ", options.stream()
                        .filter(o -> correct.contains(o.id())).map(Option::text).toList());
                // Short answers like "O(log n)" carry few terms; judge them with the stem.
                if (analyzer.termSet(correctText).size() >= 2
                        && grounding(correctText, sourceTerms) < CHOICE_GROUNDING) {
                    return reject(q, "correct option not supported by the material");
                }
            }
            cleaned.setOptions(options);
            cleaned.setCorrectAnswer(new ArrayList<>(correct));
        }

        if (isDuplicate(cleaned, accepted)) return reject(q, "near-duplicate of another question");
        return new Result(cleaned, null);
    }

    private double grounding(String text, Set<String> sourceTerms) {
        Set<String> terms = analyzer.termSet(text);
        if (terms.isEmpty()) return 0;
        return (double) terms.stream().filter(sourceTerms::contains).count() / terms.size();
    }

    boolean isDuplicate(GenQuestion q, List<GenQuestion> accepted) {
        if (accepted.isEmpty()) return false;
        List<List<String>> docs = new ArrayList<>();
        for (GenQuestion a : accepted) docs.add(analyzer.terms(a.getQuestionText()));
        List<String> mine = analyzer.terms(q.getQuestionText());
        docs.add(mine);
        TfIdf model = TfIdf.fit(docs);
        Map<String, Double> v = model.vector(mine);
        for (int i = 0; i < accepted.size(); i++) {
            if (TfIdf.cosine(v, model.vector(docs.get(i))) > DUPLICATE_SIMILARITY) return true;
        }
        return false;
    }

    private static Result reject(GenQuestion q, String why) {
        return new Result(q, why);
    }
}
