package com.lms.modules.studyai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.infrastructure.nlp.AiRateLimiter;
import com.lms.infrastructure.nlp.BloomClassifier;
import com.lms.infrastructure.nlp.BloomLevel;
import com.lms.infrastructure.nlp.LlmGateway;
import com.lms.infrastructure.nlp.NlpProperties;
import com.lms.modules.studyai.CourseContextReader.CloRow;
import com.lms.modules.studyai.dto.GradeSuggestionResponse;
import com.lms.modules.studyai.dto.QuestionAssistRequest;
import com.lms.modules.studyai.dto.QuestionAssistResponse;
import com.lms.shared.OfferingStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

/**
 * Help for questions the teacher writes by hand: a suggested Bloom level,
 * marks and CLO — and, for quiz attempts waiting for review, a suggested mark
 * for each written answer. Suggestions only: nothing is saved here.
 */
@Service
@RequiredArgsConstructor
public class QuestionAssistService {

    private final BloomClassifier bloom;
    private final CloSuggester cloSuggester;
    private final CourseContextReader context;
    private final PaperSettingsService settings;
    private final AnswerGrader grader;
    private final LlmGateway llm;
    private final OfferingStaff offeringStaff;
    private final AiRateLimiter rateLimiter;
    private final NlpProperties properties;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelView(String bloomLevel, List<String> cloCodes) {}

    private static final Map<String, Object> VIEW_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "bloomLevel", Map.of("type", "string",
                            "enum", List.of("REMEMBER", "UNDERSTAND", "APPLY", "ANALYZE", "EVALUATE", "CREATE")),
                    "cloCodes", Map.of("type", "array", "items", Map.of("type", "string"))),
            "required", List.of("bloomLevel", "cloCodes"));

    @SuppressWarnings("unchecked")
    public QuestionAssistResponse assist(QuestionAssistRequest req, UUID actorId, boolean isAdmin) {
        offeringStaff.require(req.pscId(), actorId, isAdmin);
        rateLimiter.check(actorId, "question-assist", properties.getLimits().getAssistsPerHour(), AiRateLimiter.Window.HOUR);
        List<CloRow> clos = context.clos(context.offering(req.pscId()).courseId());

        String answer = req.modelAnswer();
        if (!GenQuestion.builder().type(req.type()).build().isWritten() && req.options() != null) {
            Set<String> correct = new HashSet<>(req.correctAnswer() == null ? List.of() : req.correctAnswer());
            answer = String.join(" ", req.options().stream()
                    .filter(o -> correct.contains(String.valueOf(o.get("id"))))
                    .map(o -> String.valueOf(o.get("text"))).toList());
        }

        BloomClassifier.Classification c = bloom.classify(req.questionText(), req.type());
        String level = c.level().name();
        String source = c.source();

        Optional<ModelView> view = Optional.empty();
        if (llm.available() && !clos.isEmpty()) {
            StringBuilder p = new StringBuilder("Course learning outcomes:\n");
            clos.forEach(clo -> p.append("- ").append(clo.code()).append(": ").append(clo.title()).append('\n'));
            p.append("\nQuestion (").append(req.type()).append("): ").append(req.questionText()).append('\n');
            if (answer != null && !answer.isBlank()) p.append("Answer: ").append(answer).append('\n');
            p.append("\nWhich outcome codes does this question assess, and at which Bloom's taxonomy level?");
            view = llm.structured("You map exam questions to course learning outcomes. Output JSON only.",
                    p.toString(), VIEW_SCHEMA, ModelView.class);
        }
        if (c.matchedVerb() == null && view.isPresent()) {
            Optional<BloomLevel> fromModel = BloomLevel.parse(view.get().bloomLevel());
            if (fromModel.isPresent()) {
                level = fromModel.get().name();
                source = "model's level (no action verb in the question)";
            }
        }

        MarksPolicy table = settings.settings(req.pscId()).marks();
        String difficulty = req.difficulty() == null ? "MEDIUM" : req.difficulty();
        BigDecimal marks = table.marks(req.type(), level, difficulty);
        String breakdown = typeLabel(req.type()) + " " + PaperService.plain(table.base().get(req.type()))
                + " × " + capitalise(level) + " " + table.bloom().get(level)
                + " × " + capitalise(difficulty) + " " + table.difficulty().get(difficulty)
                + " = " + PaperService.plain(marks);

        CloSuggester.Suggestion s = cloSuggester.suggest(req.questionText(), answer, level, clos,
                view.map(ModelView::cloCodes).orElse(List.of()));
        return new QuestionAssistResponse(level, source, marks, breakdown, s.cloIds(),
                s.scores().stream().map(x -> (Map<String, Object>) mapper.convertValue(x, Map.class)).toList(),
                s.rationale(), view.isPresent());
    }

    /** Suggested marks for every written answer of a submitted quiz attempt. */
    public List<GradeSuggestionResponse> gradeSuggestions(UUID submissionId, UUID actorId, boolean isAdmin) {
        Map<String, Object> sub = jdbc.queryForList("""
                SELECT s.answers::text AS answers, q.psc_id, s.submitted_at
                FROM   quiz_submissions s JOIN quizzes q ON q.id = s.quiz_id
                WHERE  s.id = :id
                """, Map.of("id", submissionId)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz submission not found"));
        offeringStaff.require((UUID) sub.get("psc_id"), actorId, isAdmin);
        if (sub.get("submitted_at") == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This attempt has not been submitted yet");
        }
        Map<String, List<String>> answers = Sql.read(mapper, (String) sub.get("answers"), Sql.ANSWERS, Map.of());

        List<GenQuestion> written = jdbc.query("""
                SELECT qq.id, qq.type, qq.question_text, qq.model_answer, qq.rubric::text AS rubric, qq.marks
                FROM   quiz_questions qq JOIN quiz_submissions s ON s.quiz_id = qq.quiz_id
                WHERE  s.id = :id AND qq.type IN ('SHORT_ANSWER', 'LONG_ANSWER')
                ORDER  BY qq.order_index
                """, Map.of("id", submissionId),
                (rs, i) -> GenQuestion.builder().id(rs.getObject("id", UUID.class).toString())
                        .type(rs.getString("type")).questionText(rs.getString("question_text"))
                        .modelAnswer(rs.getString("model_answer")).marks(rs.getBigDecimal("marks"))
                        .rubric(new ArrayList<>(Arrays.asList(Sql.read(mapper, rs.getString("rubric"),
                                GenQuestion.RubricItem[].class, new GenQuestion.RubricItem[0]))))
                        .build());
        List<GradeSuggestionResponse> out = new ArrayList<>();
        for (GenQuestion q : written) {
            List<String> a = answers.getOrDefault(q.getId(), List.of());
            AnswerGrader.Grade g = grader.grade(q, a.isEmpty() ? "" : a.get(0));
            out.add(new GradeSuggestionResponse(q.getId(), g.score(), g.maxMarks(), g.feedback(), g.method(),
                    g.classicalScore(), g.modelScore(), g.disagreement(), g.covered(), g.missing()));
        }
        return out;
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "MCQ" -> "MCQ";
            case "MSQ" -> "MSQ";
            case "SHORT_ANSWER" -> "Short answer";
            default -> "Long answer";
        };
    }

    private static String capitalise(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
