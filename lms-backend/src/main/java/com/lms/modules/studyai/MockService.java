package com.lms.modules.studyai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.infrastructure.nlp.AiRateLimiter;
import com.lms.infrastructure.nlp.NlpExecutors;
import com.lms.infrastructure.nlp.NlpProperties;
import com.lms.modules.studyai.MockRepository.MockRow;
import com.lms.modules.studyai.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

/**
 * Practice quizzes and papers a student generates from their course material,
 * marked straight away with feedback.
 *
 * <p>Built by the same generator as the teacher's tool but from visible
 * material only, and capped by the offering's mock-paper marks limit. Stored
 * in their own table, so nothing here can reach a transcript.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockService {

    private final MockRepository repository;
    private final QuestionGenerator generator;
    private final AnswerGrader grader;
    private final StudentScope scope;
    private final PaperSettingsService settings;
    private final RetrievalService retrieval;
    private final AiRateLimiter rateLimiter;
    private final NlpProperties properties;
    private final NlpExecutors executors;
    private final ObjectMapper mapper;

    public MockAttemptResponse create(UUID studentId, MockRequest req) {
        scope.require(req.pscId(), studentId);
        PaperSettingsService.Settings s = settings.settings(req.pscId());
        // Practice papers answer to their own, smaller cap rather than the exam maximum.
        PaperSettingsService.Settings mockLimits = new PaperSettingsService.Settings(s.mockMaxTotalMarks(),
                s.maxQuestions(), s.maxDurationMinutes(), s.mockMaxTotalMarks(), s.marks());
        GenerationEstimateResponse est = QuestionGenerationService.estimate(mockLimits, req.counts(), List.of(),
                req.difficulty(), s.mockMaxTotalMarks());
        if (!est.withinLimit()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    est.problem().replace("mark limit", "mark limit for practice papers"));
        }
        rateLimiter.check(studentId, "mock-paper", properties.getLimits().getMocksPerDay(), AiRateLimiter.Window.DAY);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("counts", req.counts());
        config.put("difficulty", req.difficulty() == null ? "MEDIUM" : req.difficulty());
        config.put("topicIds", req.topicIds() == null ? List.of() : req.topicIds());
        config.put("materialIds", req.materialIds() == null ? List.of() : req.materialIds());
        config.put("durationMinutes", req.durationMinutes());
        UUID id = repository.create(studentId, req.pscId(), req.kind(), config);
        try {
            executors.tasks().submit(() -> build(id, req, s));
        } catch (RejectedExecutionException e) {
            repository.failed(id, "The generator is busy. Try again in a few minutes.");
        }
        return toResponse(repository.find(id).orElseThrow(), true);
    }

    void build(UUID id, MockRequest req, PaperSettingsService.Settings s) {
        try {
            QuestionGenerator.Result r = generator.generate(new QuestionGenerator.Spec(req.pscId(), req.counts(),
                    req.difficulty() == null ? "MEDIUM" : req.difficulty(), List.of(), List.of(),
                    s.mockMaxTotalMarks(), s.maxQuestions(), nonNull(req.topicIds()), nonNull(req.materialIds()),
                    true, null, s.marks(), new Random().nextLong(), earlierQuestions(id, req)));
            if (r.questions().isEmpty()) {
                repository.failed(id, "No questions could be written from the chosen material. Pick more topics.");
                return;
            }
            BigDecimal total = PaperAssembler.total(r.questions(), GenQuestion::getMarks);
            repository.ready(id, r.questions(), total, r.notes());
        } catch (ResponseStatusException e) {
            repository.failed(id, e.getReason());
        } catch (Exception e) {
            log.error("Mock attempt {} could not be generated", id, e);
            repository.failed(id, "The practice paper could not be generated: " + e.getMessage());
        }
    }

    /** Most of the student's earlier practice papers a new one must not repeat. */
    static final int EARLIER_ATTEMPTS = 8;

    /** Questions from the student's recent practice papers on this course, so a new paper asks new things. */
    private List<GenQuestion> earlierQuestions(UUID id, MockRequest req) {
        UUID studentId = repository.find(id).map(MockRow::studentId).orElse(null);
        if (studentId == null) return List.of();
        return repository.list(studentId, req.pscId()).stream()
                .filter(m -> !m.id().equals(id))
                .limit(EARLIER_ATTEMPTS)
                .flatMap(m -> m.questions().stream())
                .toList();
    }

    public List<MockAttemptResponse> list(UUID studentId, UUID pscId) {
        return repository.list(studentId, pscId).stream().map(r -> toResponse(r, false)).toList();
    }

    public MockAttemptResponse get(UUID id, UUID studentId) {
        return toResponse(own(id, studentId), true);
    }

    public MockAttemptResponse saveAnswers(UUID id, UUID studentId, MockAnswersRequest req) {
        MockRow m = own(id, studentId);
        if (!m.status().equals("READY")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This practice attempt is not open for answers");
        }
        repository.saveAnswers(id, req.answers());
        return toResponse(repository.find(id).orElseThrow(), true);
    }

    /** Marks every answer: choice questions exactly, written ones by {@link AnswerGrader}. */
    public MockAttemptResponse submit(UUID id, UUID studentId, MockAnswersRequest req) {
        MockRow m = own(id, studentId);
        if (!m.status().equals("READY")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This practice attempt has already been marked");
        }
        Map<String, List<String>> answers = req == null || req.answers() == null ? m.answers() : req.answers();
        Map<String, Map<String, Object>> results = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (GenQuestion q : m.questions()) {
            List<String> a = answers.getOrDefault(q.getId(), List.of());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("maxMarks", q.getMarks());
            if (q.isWritten()) {
                AnswerGrader.Grade g = grader.grade(q, a.isEmpty() ? "" : a.get(0));
                r.put("score", g.score());
                r.put("feedback", g.feedback());
                r.put("method", g.method());
                r.put("covered", g.covered());
                r.put("missing", g.missing());
                total = total.add(g.score());
            } else {
                boolean right = new HashSet<>(a).equals(new HashSet<>(q.getCorrectAnswer()));
                r.put("score", right ? q.getMarks() : BigDecimal.ZERO);
                r.put("correct", right);
                r.put("feedback", right ? "Correct." : a.isEmpty() ? "Not answered." : "Incorrect.");
                if (right) total = total.add(q.getMarks());
            }
            results.put(q.getId(), r);
        }
        repository.graded(id, answers, results, total);
        return toResponse(repository.find(id).orElseThrow(), true);
    }

    private MockRow own(UUID id, UUID studentId) {
        MockRow m = repository.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Practice attempt not found"));
        if (!m.studentId().equals(studentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Practice attempt not found");
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private MockAttemptResponse toResponse(MockRow m, boolean detail) {
        boolean graded = m.status().equals("GRADED");
        List<MockQuestionResponse> questions = null;
        if (detail) {
            Set<UUID> chunkIds = new HashSet<>();
            if (graded) m.questions().forEach(q -> chunkIds.addAll(q.getSourceChunkIds()));
            Map<UUID, CitationResponse> where = retrieval.locate(chunkIds);
            questions = m.questions().stream().map(q -> new MockQuestionResponse(q.getId(), q.getType(),
                    q.getQuestionText(), q.getOptions().stream()
                            .map(o -> (Map<String, Object>) mapper.convertValue(o, Map.class)).toList(),
                    q.getMarks(), q.getBloomLevel(),
                    graded ? q.getCorrectAnswer() : null,
                    graded ? q.getModelAnswer() : null,
                    graded ? q.getExplanation() : null,
                    graded ? q.getSourceChunkIds().stream().map(where::get).filter(Objects::nonNull).limit(2).toList()
                            : List.of())).toList();
        }
        return new MockAttemptResponse(m.id(), m.pscId(), m.kind(), m.status(), m.error(), m.config(),
                m.totalMarks(), m.score(), m.questions().size(), m.createdAt(), m.submittedAt(), questions,
                detail ? m.answers() : null, detail && graded ? m.results() : null);
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }
}
