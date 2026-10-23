package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.BloomClassifier;
import com.lms.infrastructure.nlp.BloomLevel;
import com.lms.infrastructure.nlp.LlmGateway;
import com.lms.infrastructure.nlp.TextAnalyzer;
import com.lms.modules.studyai.CourseContextReader.CloRow;
import com.lms.modules.studyai.GenQuestion.Option;
import com.lms.modules.studyai.GenQuestion.RubricItem;
import com.lms.modules.studyai.RetrievalService.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Produces a set of questions to a specification: how many of each type,
 * difficulty, Bloom levels, CLOs and a marks limit. Shared by the teacher's
 * generator and students' mock quizzes and papers.
 *
 * <ol>
 *   <li>Passages are taken from the chosen topics or materials.</li>
 *   <li>For each type the local model writes questions a few passages at a
 *       time; the template generator fills whatever it could not (or all of
 *       it, when the model is unavailable).</li>
 *   <li>Each question is validated ({@link QuestionValidator}): malformed,
 *       ungrounded or duplicate questions are dropped.</li>
 *   <li>Each is annotated: Bloom level from its action verb, marks from the
 *       offering's marks table, CLO from similarity to the CLO statements.</li>
 *   <li>The set is fitted to the marks and question limits
 *       ({@link PaperAssembler}).</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class QuestionGenerator {

    static final int SOURCE_TOKENS_PER_CALL = 1800;
    static final int MAX_CHUNKS = 80;

    private final RetrievalService retrieval;
    private final CourseContextReader context;
    private final LlmGateway llm;
    private final LlmQuestionGenerator llmGenerator;
    private final TemplateQuestionGenerator templates;
    private final QuestionValidator validator;
    private final BloomClassifier bloom;
    private final CloSuggester cloSuggester;
    private final TextAnalyzer analyzer;

    record Spec(UUID pscId, Map<String, Integer> counts, String difficulty, List<String> bloomLevels,
                List<UUID> targetCloIds, BigDecimal maxTotalMarks, int maxQuestions, List<UUID> topicIds,
                List<UUID> materialIds, boolean visibleOnly, String instructions, MarksPolicy marks, long seed,
                List<GenQuestion> avoid) {}

    record Result(List<GenQuestion> questions, String mode, List<String> notes) {}

    Result generate(Spec spec) {
        List<RetrievedChunk> chunks = retrieval.forGeneration(spec.pscId(), spec.topicIds(), spec.materialIds(),
                spec.visibleOnly(), MAX_CHUNKS);
        if (chunks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No indexed course material matches this selection yet. Upload a PDF, PowerPoint or Word "
                    + "file and wait until it shows as Indexed.");
        }
        CourseContextReader.Offering offering = context.offering(spec.pscId());
        List<CloRow> allClos = context.clos(offering.courseId());
        List<CloRow> clos = spec.targetCloIds() == null || spec.targetCloIds().isEmpty() ? allClos
                : allClos.stream().filter(c -> spec.targetCloIds().contains(c.id())).toList();
        if (clos.isEmpty()) clos = allClos;

        List<String> pool = retrieval.phrasePool(spec.pscId(), 400);
        TemplateQuestionGenerator.Run run = templates.start(chunks, pool, spec.seed());
        List<GenQuestion> accepted = new ArrayList<>();
        // Accepted questions plus ones the caller already has, for duplicate checks.
        List<GenQuestion> seen = new ArrayList<>(spec.avoid() == null ? List.of() : spec.avoid());
        List<String> notes = new ArrayList<>();
        boolean usedLlm = false, usedTemplate = false;
        int cursor = 0;

        for (String type : MarksPolicy.TYPES) {
            int want = Math.max(0, spec.counts().getOrDefault(type, 0));
            if (want == 0) continue;
            int got = 0;
            if (llm.available()) {
                int batch = type.equals("LONG_ANSWER") ? 2 : type.equals("SHORT_ANSWER") ? 3 : 5;
                int calls = (int) Math.ceil(want / (double) batch) + 1;
                for (int c = 0; c < calls && got < want; c++) {
                    List<RetrievedChunk> window = window(chunks, cursor);
                    cursor = (cursor + window.size()) % chunks.size();
                    for (GenQuestion q : llmGenerator.generate(type, Math.min(batch, want - got), spec.difficulty(),
                            spec.bloomLevels(), clos, window, spec.instructions())) {
                        if (got >= want) break;
                        QuestionValidator.Result r = validator.validate(q, sourcesOf(q, chunks), seen);
                        if (!r.ok()) {
                            log.debug("Dropped generated {}: {}", type, r.rejection());
                            continue;
                        }
                        accepted.add(annotate(r.question(), spec, clos));
                        seen.add(r.question());
                        got++;
                        usedLlm = true;
                    }
                }
            }
            if (got < want) {
                for (GenQuestion q : templates.generate(run, type, (want - got) * 2, spec.difficulty(), spec.bloomLevels())) {
                    if (got >= want) break;
                    QuestionValidator.Result r = validator.validate(q, sourcesOf(q, chunks), seen);
                    if (!r.ok()) continue;
                    accepted.add(annotate(r.question(), spec, clos));
                    seen.add(r.question());
                    got++;
                    usedTemplate = true;
                }
            }
            if (got < want) {
                notes.add("Only " + got + " of " + want + " " + label(type)
                        + " could be written from this material.");
            }
        }

        List<GenQuestion> fitted = PaperAssembler.fit(accepted, GenQuestion::getMarks, GenQuestion::getCloIds,
                GenQuestion::getBloomLevel, spec.maxTotalMarks(), spec.maxQuestions(), spec.targetCloIds());
        if (fitted.size() < accepted.size()) {
            notes.add((accepted.size() - fitted.size()) + " question(s) were left out to stay within the "
                    + spec.maxTotalMarks().stripTrailingZeros().toPlainString() + "-mark limit.");
        }
        for (int i = 0; i < fitted.size(); i++) fitted.get(i).setId("q" + (i + 1));
        String mode = usedLlm && usedTemplate ? "HYBRID" : usedLlm ? "LLM" : "TEMPLATE";
        return new Result(fitted, mode, notes);
    }

    /** Consecutive passages starting at {@code from}, up to the per-call source budget. */
    private static List<RetrievedChunk> window(List<RetrievedChunk> chunks, int from) {
        List<RetrievedChunk> out = new ArrayList<>();
        int tokens = 0;
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get((from + i) % chunks.size());
            if (!out.isEmpty() && tokens + c.tokenEstimate() > SOURCE_TOKENS_PER_CALL) break;
            out.add(c);
            tokens += c.tokenEstimate();
        }
        return out;
    }

    private static List<RetrievedChunk> sourcesOf(GenQuestion q, List<RetrievedChunk> chunks) {
        Set<UUID> ids = new HashSet<>(q.getSourceChunkIds());
        List<RetrievedChunk> s = chunks.stream().filter(c -> ids.contains(c.id())).toList();
        return s.isEmpty() ? chunks : s;
    }

    /** Bloom level, marks, rubric weights and CLO for one validated question. */
    GenQuestion annotate(GenQuestion q, Spec spec, List<CloRow> clos) {
        BloomClassifier.Classification c = bloom.classify(q.getQuestionText(), q.getType());
        String level = c.level().name();
        String source = c.source();
        if (c.matchedVerb() == null) {
            Optional<BloomLevel> fromModel = BloomLevel.parse(q.getModelBloomLevel());
            if (fromModel.isPresent()) {
                level = fromModel.get().name();
                source = "model's level (no action verb in the question)";
            }
        }
        q.setBloomLevel(level);
        q.setBloomSource(source);
        if (q.getDifficulty() == null) q.setDifficulty(spec.difficulty() == null ? "MEDIUM" : spec.difficulty());
        q.setMarks(spec.marks().marks(q.getType(), level, q.getDifficulty()));
        if (q.isWritten()) q.setRubric(scaleRubric(q.getRubric(), q.getMarks(), q.getModelAnswer()));

        CloSuggester.Suggestion s = cloSuggester.suggest(q.getQuestionText(), answerText(q), level, clos,
                q.getModelCloCodes());
        q.setCloIds(new ArrayList<>(s.cloIds()));
        q.setCloScores(new ArrayList<>(s.scores()));
        q.setCloRationale(s.rationale());
        return q;
    }

    static String answerText(GenQuestion q) {
        if (q.isWritten()) return q.getModelAnswer();
        return String.join(" ", q.getOptions().stream()
                .filter(o -> q.getCorrectAnswer().contains(o.id())).map(Option::text).toList());
    }

    /** Spreads the question's marks over its rubric in proportion to the criteria's weights. */
    List<RubricItem> scaleRubric(List<RubricItem> rubric, BigDecimal marks, String modelAnswer) {
        List<RubricItem> items = rubric == null ? List.of() : rubric;
        if (items.isEmpty()) {
            List<String> kws = analyzer.keyPhrases(modelAnswer == null ? "" : modelAnswer, 4).stream()
                    .map(TextAnalyzer.KeyPhrase::text).toList();
            return List.of(new RubricItem("Covers the points of the model answer", marks, kws));
        }
        if (items.size() > 6) items = items.subList(0, 6);
        BigDecimal weights = items.stream().map(i -> i.marks() == null || i.marks().signum() <= 0
                ? BigDecimal.ONE : i.marks()).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<RubricItem> out = new ArrayList<>();
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            RubricItem it = items.get(i);
            BigDecimal w = it.marks() == null || it.marks().signum() <= 0 ? BigDecimal.ONE : it.marks();
            BigDecimal m;
            if (i == items.size() - 1) {
                m = marks.subtract(assigned);
            } else {
                m = MarksPolicy.roundHalf(marks.multiply(w).divide(weights, 4, RoundingMode.HALF_UP));
                // Never let earlier rows use up more than the question has.
                BigDecimal room = marks.subtract(assigned).subtract(new BigDecimal("0.5")
                        .multiply(BigDecimal.valueOf(items.size() - 1 - i)));
                if (m.compareTo(room) > 0) m = room.max(BigDecimal.ZERO);
            }
            if (m.signum() <= 0) continue;
            assigned = assigned.add(m);
            out.add(new RubricItem(it.criterion(), m.stripTrailingZeros(), it.keywords() == null ? List.of() : it.keywords()));
        }
        if (out.isEmpty()) out.add(new RubricItem("Covers the points of the model answer", marks, List.of()));
        return out;
    }

    static String label(String type) {
        return switch (type) {
            case "MCQ" -> "multiple-choice questions";
            case "MSQ" -> "multiple-select questions";
            case "SHORT_ANSWER" -> "short-answer questions";
            default -> "long-answer questions";
        };
    }
}
