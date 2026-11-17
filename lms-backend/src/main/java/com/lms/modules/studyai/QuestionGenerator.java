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
 *   <li>Passages are taken from the chosen topics or materials, spread over
 *       the whole selection and without front matter.</li>
 *   <li>The local model writes the questions a window of passages at a time.
 *       The windows are spread over the material and each is asked for its
 *       share of every type, one call per type: the calls on one window
 *       share their prompt prefix, which Ollama keeps cached, so the passages
 *       are read once rather than once per call. Each call lists the
 *       questions already written (and the caller's, e.g. earlier practice
 *       papers) so the model does not repeat them. The template generator
 *       fills whatever the model could not (or all of it, when the model is
 *       unavailable).</li>
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

    /**
     * Passage text per window. Reading the prompt is a large share of a CPU
     * call, so windows are kept small and reused across question types.
     */
    static final int SOURCE_TOKENS_PER_CALL = 1100;
    static final int MAX_CHUNKS = 80;
    /** Questions asked of one window across all types, roughly. */
    static final int QUESTIONS_PER_WINDOW = 6;
    /** Most questions requested in one call, per type. */
    static final Map<String, Integer> BATCH = Map.of("MCQ", 5, "MSQ", 4, "SHORT_ANSWER", 3, "LONG_ANSWER", 2);
    /** Model calls in a row that return nothing before the rest is left to the templates. */
    static final int MAX_EMPTY_CALLS = 2;

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
                spec.visibleOnly(), MAX_CHUNKS, spec.seed());
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

        Map<String, Integer> want = new LinkedHashMap<>();
        for (String type : MarksPolicy.TYPES) want.put(type, Math.max(0, spec.counts().getOrDefault(type, 0)));
        Map<String, Integer> got = new HashMap<>();
        int totalWant = want.values().stream().mapToInt(Integer::intValue).sum();

        if (llm.available() && totalWant > 0) {
            List<List<RetrievedChunk>> windows = windows(chunks, SOURCE_TOKENS_PER_CALL);
            int spread = Math.max(1, Math.min(windows.size(), (int) Math.ceil(totalWant / (double) QUESTIONS_PER_WINDOW)));
            List<List<RetrievedChunk>> plan = pick(windows, spread, spec.seed());
            int emptyCalls = 0;
            // First pass: each window its share of every type. Then top-ups on further windows.
            int extraWindows = Math.min(windows.size(), spread + 2);
            outer:
            for (int w = 0; w < extraWindows; w++) {
                List<RetrievedChunk> window = w < plan.size() ? plan.get(w)
                        : windows.get(Math.floorMod(windows.indexOf(plan.get(plan.size() - 1)) + w - plan.size() + 1,
                                windows.size()));
                for (String type : MarksPolicy.TYPES) {
                    int remaining = want.get(type) - got.getOrDefault(type, 0);
                    if (remaining <= 0) continue;
                    int windowsLeft = Math.max(1, plan.size() - w);
                    int share = w < plan.size() ? (int) Math.ceil(remaining / (double) windowsLeft) : remaining;
                    while (share > 0) {
                        int ask = Math.min(share, BATCH.getOrDefault(type, 3));
                        share -= ask;
                        List<GenQuestion> written = llmGenerator.generate(type, ask, spec.difficulty(),
                                spec.bloomLevels(), window, spec.instructions(), avoidList(seen, window));
                        if (written.isEmpty()) {
                            if (++emptyCalls >= MAX_EMPTY_CALLS) {
                                log.info("Local model returned nothing {} times; templates write the rest", emptyCalls);
                                break outer;
                            }
                            continue;
                        }
                        emptyCalls = 0;
                        for (GenQuestion q : written) {
                            if (got.getOrDefault(type, 0) >= want.get(type)) break;
                            QuestionValidator.Result r = validator.validate(q, sourcesOf(q, chunks), seen);
                            if (!r.ok()) {
                                log.debug("Dropped generated {}: {}", type, r.rejection());
                                continue;
                            }
                            accepted.add(annotate(r.question(), spec, clos));
                            seen.add(r.question());
                            got.merge(type, 1, Integer::sum);
                            usedLlm = true;
                        }
                    }
                }
                if (want.entrySet().stream().allMatch(e -> got.getOrDefault(e.getKey(), 0) >= e.getValue())) break;
            }
        }

        for (String type : MarksPolicy.TYPES) {
            int wanted = want.get(type);
            int have = got.getOrDefault(type, 0);
            if (wanted == 0) continue;
            if (have < wanted) {
                for (GenQuestion q : templates.generate(run, type, (wanted - have) * 3, spec.difficulty(), spec.bloomLevels())) {
                    if (have >= wanted) break;
                    QuestionValidator.Result r = validator.validate(q, sourcesOf(q, chunks), seen);
                    if (!r.ok()) continue;
                    accepted.add(annotate(r.question(), spec, clos));
                    seen.add(r.question());
                    have++;
                    usedTemplate = true;
                }
            }
            if (have < wanted) {
                notes.add("Only " + have + " of " + wanted + " " + label(type)
                        + " could be written from this material without repeating or weakening questions.");
            }
        }

        // Types were written window by window; present them grouped by type, as a paper is.
        accepted.sort(Comparator.comparingInt(q -> MarksPolicy.TYPES.indexOf(q.getType())));
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

    /** Consecutive, non-overlapping windows of passages, each within the source budget, in document order. */
    static List<List<RetrievedChunk>> windows(List<RetrievedChunk> chunks, int tokenBudget) {
        List<List<RetrievedChunk>> out = new ArrayList<>();
        List<RetrievedChunk> current = new ArrayList<>();
        int tokens = 0;
        for (RetrievedChunk c : chunks) {
            if (!current.isEmpty() && tokens + c.tokenEstimate() > tokenBudget) {
                out.add(current);
                current = new ArrayList<>();
                tokens = 0;
            }
            current.add(c);
            tokens += c.tokenEstimate();
        }
        if (!current.isEmpty()) out.add(current);
        return out;
    }

    /** {@code n} windows spread evenly over the material, starting at a point chosen by the seed. */
    static List<List<RetrievedChunk>> pick(List<List<RetrievedChunk>> windows, int n, long seed) {
        double step = windows.size() / (double) n;
        double offset = Math.floorMod(seed, 997) / 997.0 * step;
        List<List<RetrievedChunk>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<RetrievedChunk> w = windows.get((int) Math.min(windows.size() - 1, Math.floor(offset + i * step)));
            if (!out.contains(w)) out.add(w);
        }
        return out;
    }

    /**
     * The existing questions worth showing the model before it writes from
     * {@code window}: everything written in this run, then the caller's
     * questions that share the most terms with the window.
     */
    List<String> avoidList(List<GenQuestion> seen, List<RetrievedChunk> window) {
        if (seen.isEmpty()) return List.of();
        Set<String> windowTerms = new HashSet<>();
        window.forEach(c -> windowTerms.addAll(analyzer.termSet(c.content())));
        record Scored(String text, long overlap) {}
        return seen.stream()
                .filter(q -> q.getQuestionText() != null)
                .map(q -> new Scored(q.getQuestionText(),
                        analyzer.termSet(q.getQuestionText()).stream().filter(windowTerms::contains).count()))
                .filter(sc -> sc.overlap() >= 2)
                .sorted(Comparator.comparingLong(Scored::overlap).reversed())
                .limit(LlmQuestionGenerator.AVOID_IN_PROMPT)
                .map(Scored::text)
                .toList();
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
