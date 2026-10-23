package com.lms.modules.studyai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.infrastructure.nlp.AiRateLimiter;
import com.lms.infrastructure.nlp.NlpExecutors;
import com.lms.infrastructure.nlp.NlpProperties;
import com.lms.modules.assessment.QuizAuthoringApi;
import com.lms.modules.assessment.dto.CreateQuizQuestionRequest;
import com.lms.modules.studyai.GenQuestion.Option;
import com.lms.modules.studyai.GenQuestion.RubricItem;
import com.lms.modules.studyai.GenerationRepository.DraftRow;
import com.lms.modules.studyai.GenerationRepository.JobRow;
import com.lms.modules.studyai.dto.*;
import com.lms.shared.OfferingStaff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

/**
 * The teacher's question generator: checks a request against the marks
 * limit, runs it in the background, and lets the teacher review, edit and
 * save the drafts into a quiz or a question paper.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionGenerationService {

    private final GenerationRepository repository;
    private final QuestionGenerator generator;
    private final PaperSettingsService settings;
    private final PaperService papers;
    private final QuizAuthoringApi quizzes;
    private final CourseContextReader context;
    private final OfferingStaff offeringStaff;
    private final AiRateLimiter rateLimiter;
    private final NlpProperties properties;
    private final NlpExecutors executors;
    private final ObjectMapper mapper;

    // ── Estimate and limits ──────────────────────────────────────────────────

    /**
     * What a requested mix comes to in marks, and whether it fits. The same
     * check refuses a job before anything is generated, so a paper can never
     * come out longer than the limit the teacher set.
     */
    public GenerationEstimateResponse estimate(UUID pscId, GenerationRequest req, UUID actorId, boolean isAdmin) {
        offeringStaff.require(pscId, actorId, isAdmin);
        return estimate(settings.settings(pscId), req.counts(), req.bloomLevels(), req.difficulty(), req.maxTotalMarks());
    }

    static GenerationEstimateResponse estimate(PaperSettingsService.Settings s, Map<String, Integer> counts,
                                               List<String> bloomLevels, String difficulty, BigDecimal maxTotalMarks) {
        Map<String, BigDecimal> per = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        int questions = 0;
        for (String type : MarksPolicy.TYPES) {
            BigDecimal m = s.marks().estimate(type, bloomLevels, difficulty);
            per.put(type, m);
            int n = Math.max(0, counts.getOrDefault(type, 0));
            questions += n;
            total = total.add(m.multiply(BigDecimal.valueOf(n)));
        }
        String problem = null;
        for (String type : counts.keySet()) {
            if (!MarksPolicy.TYPES.contains(type)) problem = "Unknown question type " + type;
        }
        if (problem == null && questions == 0) problem = "Ask for at least one question.";
        else if (problem == null && maxTotalMarks.compareTo(s.maxTotalMarks()) > 0) {
            problem = "The marks limit may not exceed " + PaperService.plain(s.maxTotalMarks())
                    + ", this course's maximum for a paper.";
        } else if (problem == null && questions > s.maxQuestions()) {
            problem = questions + " questions is more than this course's limit of " + s.maxQuestions() + " per paper.";
        } else if (problem == null && total.compareTo(maxTotalMarks) > 0) {
            problem = "This mix comes to about " + PaperService.plain(total) + " marks, "
                    + PaperService.plain(total.subtract(maxTotalMarks)) + " over the "
                    + PaperService.plain(maxTotalMarks) + "-mark limit. Ask for fewer questions or raise the limit.";
        }
        return new GenerationEstimateResponse(total, maxTotalMarks, s.maxTotalMarks(), questions, s.maxQuestions(),
                per, problem == null, problem);
    }

    // ── Jobs ─────────────────────────────────────────────────────────────────

    public GenerationJobResponse createJob(UUID pscId, GenerationRequest req, UUID actorId, boolean isAdmin) {
        offeringStaff.require(pscId, actorId, isAdmin);
        PaperSettingsService.Settings s = settings.settings(pscId);
        GenerationEstimateResponse est = estimate(s, req.counts(), req.bloomLevels(), req.difficulty(), req.maxTotalMarks());
        if (!est.withinLimit()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, est.problem());
        rateLimiter.check(actorId, "question-generation", properties.getLimits().getGenerationJobsPerDay(),
                AiRateLimiter.Window.DAY);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("counts", req.counts());
        config.put("difficulty", req.difficulty() == null ? "MEDIUM" : req.difficulty());
        config.put("bloomLevels", req.bloomLevels() == null ? List.of() : req.bloomLevels());
        config.put("targetCloIds", req.targetCloIds() == null ? List.of() : req.targetCloIds());
        config.put("maxTotalMarks", req.maxTotalMarks());
        config.put("instructions", req.instructions());
        UUID jobId = repository.createJob(pscId, actorId, config,
                nonNull(req.materialIds()), nonNull(req.topicIds()));
        try {
            executors.tasks().submit(() -> run(jobId, req, s));
        } catch (RejectedExecutionException e) {
            repository.markFailed(jobId, "The generator is busy. Try again in a few minutes.");
        }
        return repository.job(jobId);
    }

    void run(UUID jobId, GenerationRequest req, PaperSettingsService.Settings s) {
        JobRow job = repository.findJob(jobId).orElseThrow();
        repository.markRunning(jobId);
        try {
            QuestionGenerator.Result result = generator.generate(spec(job.pscId(), req, s, List.of()));
            if (result.questions().isEmpty()) {
                repository.markFailed(jobId, "No questions could be written from the selected material. "
                        + "Choose more topics or materials.");
                return;
            }
            repository.complete(jobId, result.questions(), result.mode(), result.notes());
        } catch (ResponseStatusException e) {
            repository.markFailed(jobId, e.getReason());
        } catch (Exception e) {
            log.error("Question generation job {} failed", jobId, e);
            repository.markFailed(jobId, "Generation failed: " + e.getMessage());
        }
    }

    private QuestionGenerator.Spec spec(UUID pscId, GenerationRequest req, PaperSettingsService.Settings s,
                                        List<GenQuestion> avoid) {
        return new QuestionGenerator.Spec(pscId, req.counts(), req.difficulty() == null ? "MEDIUM" : req.difficulty(),
                nonNull(req.bloomLevels()), nonNull(req.targetCloIds()), req.maxTotalMarks(), s.maxQuestions(),
                nonNull(req.topicIds()), nonNull(req.materialIds()), false, req.instructions(), s.marks(),
                new Random().nextLong(), avoid);
    }

    public List<GenerationJobResponse> listJobs(UUID pscId, UUID actorId, boolean isAdmin) {
        offeringStaff.require(pscId, actorId, isAdmin);
        return repository.listJobs(pscId);
    }

    public GenerationJobResponse getJob(UUID jobId, UUID actorId, boolean isAdmin) {
        return repository.job(requireJob(jobId, actorId, isAdmin).id());
    }

    // ── Drafts ───────────────────────────────────────────────────────────────

    public QuestionDraftResponse updateDraft(UUID draftId, UpdateDraftRequest req, UUID actorId, boolean isAdmin) {
        DraftRow d = requireDraft(draftId, actorId, isAdmin);
        GenQuestion q = d.question();
        q.setQuestionText(req.questionText().strip());
        q.setOptions(new ArrayList<>(req.options().stream()
                .map(m -> new Option(String.valueOf(m.get("id")), String.valueOf(m.get("text")))).toList()));
        q.setCorrectAnswer(new ArrayList<>(req.correctAnswer()));
        q.setModelAnswer(req.modelAnswer());
        q.setRubric(new ArrayList<>(req.rubric() == null ? List.of() : req.rubric().stream()
                .map(m -> mapper.convertValue(m, RubricItem.class)).toList()));
        q.setExplanation(req.explanation());
        q.setMarks(req.marks());
        if (req.bloomLevel() != null && !req.bloomLevel().equals(q.getBloomLevel())) {
            q.setBloomLevel(req.bloomLevel());
            q.setBloomSource("set by teacher");
        }
        if (req.difficulty() != null) q.setDifficulty(req.difficulty());
        Set<UUID> courseClos = new HashSet<>();
        context.clos(context.offering(d.pscId()).courseId()).forEach(c -> courseClos.add(c.id()));
        if (!courseClos.containsAll(req.cloIds())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every CLO must belong to this course");
        }
        q.setCloIds(new ArrayList<>(req.cloIds()));
        validateDraft(q);
        repository.replaceDraft(draftId, q, req.status() == null ? d.status() : req.status(), true);
        return repository.draftResponse(draftId);
    }

    /** Replaces one draft with a fresh question of the same type from the same material. */
    public QuestionDraftResponse regenerateDraft(UUID draftId, UUID actorId, boolean isAdmin) {
        DraftRow d = requireDraft(draftId, actorId, isAdmin);
        JobRow job = repository.findJob(d.jobId()).orElseThrow();
        rateLimiter.check(actorId, "question-regeneration", properties.getLimits().getAssistsPerHour(),
                AiRateLimiter.Window.HOUR);
        PaperSettingsService.Settings s = settings.settings(job.pscId());
        GenerationRequest req = requestOf(job, Map.of(d.question().getType(), 1));
        List<GenQuestion> avoid = repository.drafts(d.jobId()).stream()
                .map(r -> GenQuestion.builder().questionText(r.questionText()).build()).toList();
        QuestionGenerator.Result result = generator.generate(spec(job.pscId(),
                new GenerationRequest(req.materialIds(), req.topicIds(), req.counts(), req.difficulty(),
                        req.bloomLevels(), req.targetCloIds(), BigDecimal.valueOf(1000), null), s, avoid));
        if (result.questions().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No different question of this type could be written from this material.");
        }
        repository.replaceDraft(draftId, result.questions().get(0), "PENDING", false);
        return repository.draftResponse(draftId);
    }

    @SuppressWarnings("unchecked")
    private GenerationRequest requestOf(JobRow job, Map<String, Integer> counts) {
        Map<String, Object> c = job.config();
        List<String> levels = c.get("bloomLevels") instanceof List<?> l ? (List<String>) l : List.of();
        List<UUID> clos = c.get("targetCloIds") instanceof List<?> l
                ? l.stream().map(x -> UUID.fromString(String.valueOf(x))).toList() : List.of();
        return new GenerationRequest(job.materialIds(), job.topicIds(), counts,
                (String) c.getOrDefault("difficulty", "MEDIUM"), levels, clos,
                new BigDecimal(String.valueOf(c.getOrDefault("maxTotalMarks", "100"))),
                (String) c.get("instructions"));
    }

    // ── Saving ───────────────────────────────────────────────────────────────

    @Transactional
    public SaveDraftsResponse save(UUID jobId, SaveDraftsRequest req, UUID actorId, boolean isAdmin) {
        JobRow job = requireJob(jobId, actorId, isAdmin);
        List<DraftRow> drafts = repository.findDrafts(req.draftIds()).stream()
                .filter(d -> d.jobId().equals(jobId)).toList();
        if (drafts.size() != new HashSet<>(req.draftIds()).size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Some drafts are not part of this job");
        }
        drafts.forEach(d -> validateDraft(d.question()));

        UUID targetId;
        if (req.target().equals("QUIZ")) {
            targetId = req.quizId() != null ? req.quizId() : createQuiz(job.pscId(), req.newQuiz(), actorId, isAdmin);
            if (!quizzes.getQuiz(targetId).getPscId().equals(job.pscId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That quiz belongs to another course");
            }
            quizzes.addQuestions(targetId, drafts.stream().map(d -> toQuizQuestion(d.question())).toList(),
                    actorId, isAdmin);
        } else {
            if (req.paperId() != null) {
                targetId = req.paperId();
            } else {
                if (req.newPaper() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a paper or name a new one");
                }
                BigDecimal limit = req.newPaper().maxTotalMarks() != null ? req.newPaper().maxTotalMarks()
                        : new BigDecimal(String.valueOf(job.config().getOrDefault("maxTotalMarks", "100")));
                targetId = papers.create(job.pscId(), new PaperRequest(req.newPaper().title(),
                        req.newPaper().instructions(), req.newPaper().durationMinutes(), limit), actorId, isAdmin).id();
            }
            PaperRepository.PaperRow paper = papers.requireRow(targetId, actorId, isAdmin);
            if (!paper.pscId().equals(job.pscId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That paper belongs to another course");
            }
            papers.add(paper, drafts.stream().map(d -> new PaperService.Candidate(d.question(), null, d.id())).toList(),
                    false);
        }
        repository.setStatus(req.draftIds(), "ACCEPTED");
        return new SaveDraftsResponse(req.target(), targetId, drafts.size());
    }

    private UUID createQuiz(UUID pscId, SaveDraftsRequest.NewQuiz q, UUID actorId, boolean isAdmin) {
        if (q == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a quiz or name a new one");
        return quizzes.createQuiz(pscId, q.title(), q.description(), q.durationMinutes(), actorId, isAdmin).getId();
    }

    private CreateQuizQuestionRequest toQuizQuestion(GenQuestion q) {
        CreateQuizQuestionRequest r = new CreateQuizQuestionRequest();
        r.setQuestionText(q.getQuestionText());
        r.setType(q.getType());
        r.setOptions(q.getOptions().stream().map(o -> Map.<String, Object>of("id", o.id(), "text", o.text())).toList());
        r.setCorrectAnswer(q.getCorrectAnswer());
        r.setMarks(q.getMarks());
        r.setExplanation(q.getExplanation());
        r.setBloomLevel(q.getBloomLevel());
        r.setDifficulty(q.getDifficulty());
        r.setModelAnswer(q.getModelAnswer());
        r.setRubric(q.getRubric().stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("criterion", i.criterion());
            m.put("marks", i.marks());
            m.put("keywords", i.keywords() == null ? List.of() : i.keywords());
            return m;
        }).toList());
        r.setCloIds(q.getCloIds());
        r.setAiGenerated(true);
        return r;
    }

    /** The same rules a quiz question must meet, checked before anything is saved. */
    private static void validateDraft(GenQuestion q) {
        if (q.isWritten()) {
            if (q.getModelAnswer() == null || q.getModelAnswer().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "\"" + abbreviate(q.getQuestionText()) + "\" needs a model answer");
            }
            BigDecimal rubric = q.getRubric().stream().map(RubricItem::marks).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (rubric.compareTo(q.getMarks()) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The rubric of \"" + abbreviate(q.getQuestionText()) + "\" adds up to more than its marks");
            }
            return;
        }
        Set<String> ids = new HashSet<>();
        q.getOptions().forEach(o -> ids.add(o.id()));
        if (q.getOptions().size() < 2 || q.getCorrectAnswer().isEmpty() || !ids.containsAll(q.getCorrectAnswer())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "\"" + abbreviate(q.getQuestionText()) + "\" needs options and a correct answer among them");
        }
        if ("MCQ".equals(q.getType()) && q.getCorrectAnswer().size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "\"" + abbreviate(q.getQuestionText()) + "\" is single-answer but has several correct options");
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 50 ? s : s.substring(0, 47) + "…";
    }

    private JobRow requireJob(UUID jobId, UUID actorId, boolean isAdmin) {
        JobRow job = repository.findJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Generation job not found"));
        offeringStaff.require(job.pscId(), actorId, isAdmin);
        return job;
    }

    private DraftRow requireDraft(UUID draftId, UUID actorId, boolean isAdmin) {
        DraftRow d = repository.findDraft(draftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
        offeringStaff.require(d.pscId(), actorId, isAdmin);
        return d;
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }
}
