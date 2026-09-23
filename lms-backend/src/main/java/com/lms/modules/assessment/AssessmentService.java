package com.lms.modules.assessment;

import com.lms.infrastructure.messaging.KafkaEventPublisher;
import com.lms.modules.assessment.dto.*;
import com.lms.shared.OfferingStaff;
import com.lms.shared.events.AssessmentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AssessmentService {

    /** How late a timed submit's answers may arrive and still count. */
    static final Duration SUBMIT_GRACE = Duration.ofSeconds(30);

    private final AssignmentRepository             assignmentRepository;
    private final AssignmentSubmissionRepository   submissionRepository;
    private final QuizRepository                   quizRepository;
    private final QuizQuestionRepository           questionRepository;
    private final QuizSubmissionRepository         quizSubmissionRepository;
    private final AssignmentCloMappingRepository   assignmentCloMappingRepository;
    private final QuizCloMappingRepository         quizCloMappingRepository;
    private final KafkaEventPublisher              kafkaEventPublisher;
    /**
     * Staff writes (and reads of submissions and answer keys) are limited to
     * offerings the user runs; admins pass. See {@link OfferingStaff}.
     */
    private final OfferingStaff                    offeringStaff;

    // ── Assignments ───────────────────────────────────────────────────────────

    public AssignmentResponse createAssignment(UUID createdBy, boolean isAdmin, CreateAssignmentRequest req) {
        offeringStaff.require(req.getPscId(), createdBy, isAdmin);
        Assignment a = new Assignment();
        a.setPscId(req.getPscId());
        a.setCreatedBy(createdBy);
        a.setTitle(req.getTitle());
        a.setDescription(req.getDescription());
        a.setSubmissionType(req.getSubmissionType());
        a.setTotalMarks(req.getTotalMarks());
        a.setDueDate(req.getDueDate());
        a.setAllowLateSubmission(req.isAllowLateSubmission());
        a.setLatePenaltyPercent(req.getLatePenaltyPercent());
        Assignment saved = assignmentRepository.save(a);

        announceToClass(AssessmentEvent.Action.ASSIGNMENT_CREATED, saved.getPscId(),
                saved.getId(), saved.getTitle(),
                "Due " + saved.getDueDate() + " · " + saved.getTotalMarks() + " marks");

        return toAssignmentResponse(saved);
    }

    /**
     * Tells a class that new work exists.
     *
     * <p>Creating an assignment, quiz or material used to be silent: the only
     * assessment events published were about *submissions*, so students were
     * never told there was anything to submit. The roster is resolved by the
     * notifications module, so one event covers the whole class.
     */
    private void announceToClass(AssessmentEvent.Action action, UUID pscId,
                                 UUID assessmentId, String title, String detail) {
        try {
            kafkaEventPublisher.publishAssessmentEventAfterCommit(AssessmentEvent.builder()
                    .action(action)
                    .pscId(pscId)
                    .assessmentId(assessmentId)
                    .assessmentTitle(title)
                    .detail(detail)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish {} event: {}", action, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> listAssignments(UUID pscId) {
        return assignmentRepository.findAllByPscIdOrderByDueDateAsc(pscId)
                .stream().map(this::toAssignmentResponse).toList();
    }

    @Transactional(readOnly = true)
    public AssignmentResponse getAssignment(UUID id) {
        return toAssignmentResponse(findAssignment(id));
    }

    public AssignmentResponse updateAssignment(UUID id, UpdateAssignmentRequest req,
                                               UUID actorId, boolean isAdmin) {
        Assignment a = findAssignment(id);
        offeringStaff.require(a.getPscId(), actorId, isAdmin);
        a.setTitle(req.getTitle());
        a.setDescription(req.getDescription());
        a.setTotalMarks(req.getTotalMarks());
        a.setDueDate(req.getDueDate());
        a.setAllowLateSubmission(req.isAllowLateSubmission());
        a.setLatePenaltyPercent(req.getLatePenaltyPercent());
        return toAssignmentResponse(assignmentRepository.save(a));
    }

    public void deleteAssignment(UUID id, UUID actorId, boolean isAdmin) {
        offeringStaff.require(findAssignment(id).getPscId(), actorId, isAdmin);
        assignmentRepository.deleteById(id);
    }

    // ── Assignment Submissions ────────────────────────────────────────────────

    public AssignmentSubmissionResponse submitAssignment(UUID assignmentId, UUID studentId,
                                                         SubmitAssignmentRequest req) {
        Assignment assignment = findAssignment(assignmentId);

        if (!assignmentRepository.isStudentEnrolled(assignmentId, studentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not enrolled in this course offering");
        }

        validateSubmissionContent(assignment.getSubmissionType(), req);

        AssignmentSubmission sub = submissionRepository
                .findByAssignmentIdAndStudentId(assignmentId, studentId)
                .orElseGet(AssignmentSubmission::new);

        if ("GRADED".equals(sub.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment already graded");
        }

        boolean isLate = LocalDateTime.now().isAfter(assignment.getDueDate());
        if (isLate && !assignment.isAllowLateSubmission()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Submission deadline has passed");
        }

        sub.setAssignmentId(assignmentId);
        sub.setStudentId(studentId);
        sub.setTextContent(req.getTextContent());
        sub.setFileKey(req.getFileKey());
        sub.setFileName(req.getFileName());
        sub.setFileSize(req.getFileSize());
        sub.setStatus(isLate ? "LATE" : "SUBMITTED");
        sub.setSubmittedAt(LocalDateTime.now());

        AssignmentSubmission saved = submissionRepository.save(sub);

        try {
            AssessmentContextView ctx = assignmentRepository.findEventContext(assignmentId, studentId);
            kafkaEventPublisher.publishAssessmentEvent(AssessmentEvent.builder()
                    .action(AssessmentEvent.Action.ASSIGNMENT_SUBMITTED)
                    .studentId(studentId)
                    .studentEmail(ctx.getStudentEmail())
                    .studentName(ctx.getStudentName())
                    .assessmentId(assignmentId)
                    .assessmentTitle(assignment.getTitle())
                    .courseCode(ctx.getCourseCode())
                    .courseName(ctx.getCourseName())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish ASSIGNMENT_SUBMITTED event: {}", e.getMessage());
        }

        return toSubmissionResponse(saved);
    }

    public AssignmentSubmissionResponse gradeSubmission(UUID submissionId, UUID gradedBy,
                                                         boolean isAdmin, GradeSubmissionRequest req) {
        AssignmentSubmission sub = findSubmission(submissionId);
        Assignment assignment = findAssignment(sub.getAssignmentId());
        offeringStaff.require(assignment.getPscId(), gradedBy, isAdmin);

        if (req.getMarksObtained().compareTo(assignment.getTotalMarks()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Marks obtained cannot exceed total marks (" + assignment.getTotalMarks() + ")");
        }

        BigDecimal awarded = applyLatePenalty(assignment, sub, req.getMarksObtained());

        sub.setMarksObtained(awarded);
        sub.setFeedback(req.getFeedback());
        sub.setGradedBy(gradedBy);
        sub.setGradedAt(LocalDateTime.now());
        sub.setStatus("GRADED");

        AssignmentSubmission saved = submissionRepository.save(sub);

        try {
            AssessmentContextView ctx = assignmentRepository.findEventContext(
                    assignment.getId(), sub.getStudentId());
            kafkaEventPublisher.publishAssessmentEvent(AssessmentEvent.builder()
                    .action(AssessmentEvent.Action.ASSIGNMENT_GRADED)
                    .studentId(sub.getStudentId())
                    .studentEmail(ctx.getStudentEmail())
                    .studentName(ctx.getStudentName())
                    .assessmentId(assignment.getId())
                    .assessmentTitle(assignment.getTitle())
                    .courseCode(ctx.getCourseCode())
                    .courseName(ctx.getCourseName())
                    .marksObtained(awarded.doubleValue())
                    .totalMarks(assignment.getTotalMarks().doubleValue())
                    .feedback(req.getFeedback())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish ASSIGNMENT_GRADED event: {}", e.getMessage());
        }

        return toSubmissionResponse(saved);
    }

    @Transactional(readOnly = true)
    public AssignmentSubmissionResponse getSubmission(UUID submissionId, UUID actorId, boolean isAdmin) {
        AssignmentSubmission sub = findSubmission(submissionId);
        offeringStaff.require(findAssignment(sub.getAssignmentId()).getPscId(), actorId, isAdmin);
        return toSubmissionResponse(sub, submitters(List.of(sub.getStudentId())).get(sub.getStudentId()));
    }

    @Transactional(readOnly = true)
    public List<AssignmentSubmissionResponse> listSubmissions(UUID assignmentId,
                                                              UUID actorId, boolean isAdmin) {
        offeringStaff.require(findAssignment(assignmentId).getPscId(), actorId, isAdmin);
        List<AssignmentSubmission> subs = submissionRepository.findAllByAssignmentId(assignmentId);
        Map<UUID, SubmitterView> who = submitters(subs.stream().map(AssignmentSubmission::getStudentId).toList());
        return subs.stream().map(sub -> toSubmissionResponse(sub, who.get(sub.getStudentId()))).toList();
    }

    @Transactional(readOnly = true)
    public AssignmentSubmissionResponse getMySubmission(UUID assignmentId, UUID studentId) {
        return submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)
                .map(this::toSubmissionResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
    }

    // ── Quizzes ───────────────────────────────────────────────────────────────

    public QuizResponse createQuiz(UUID createdBy, boolean isAdmin, CreateQuizRequest req) {
        offeringStaff.require(req.getPscId(), createdBy, isAdmin);
        Quiz q = new Quiz();
        q.setPscId(req.getPscId());
        q.setCreatedBy(createdBy);
        q.setTitle(req.getTitle());
        q.setDescription(req.getDescription());
        q.setDurationMinutes(req.getDurationMinutes());
        q.setAvailableFrom(req.getAvailableFrom());
        q.setAvailableUntil(req.getAvailableUntil());
        q.setShuffleQuestions(req.isShuffleQuestions());
        q.setShuffleOptions(req.isShuffleOptions());
        Quiz saved = quizRepository.save(q);

        announceToClass(AssessmentEvent.Action.QUIZ_CREATED, saved.getPscId(),
                saved.getId(), saved.getTitle(), availabilityDetail(saved));

        return toQuizResponse(saved);
    }

    private String availabilityDetail(Quiz quiz) {
        StringBuilder detail = new StringBuilder();
        if (quiz.getAvailableFrom() != null) {
            detail.append("Opens ").append(quiz.getAvailableFrom());
        }
        if (quiz.getAvailableUntil() != null) {
            detail.append(detail.isEmpty() ? "Closes " : " · closes ")
                  .append(quiz.getAvailableUntil());
        }
        if (quiz.getDurationMinutes() != null) {
            detail.append(detail.isEmpty() ? "" : " · ")
                  .append(quiz.getDurationMinutes()).append(" minutes");
        }
        return detail.isEmpty() ? null : detail.toString();
    }

    @Transactional(readOnly = true)
    public List<QuizResponse> listQuizzes(UUID pscId) {
        return quizRepository.findAllByPscIdOrderByCreatedAtDesc(pscId)
                .stream().map(this::toQuizResponse).toList();
    }

    @Transactional(readOnly = true)
    public QuizResponse getQuiz(UUID id) {
        return toQuizResponse(findQuiz(id));
    }

    public QuizResponse updateQuiz(UUID id, UpdateQuizRequest req, UUID actorId, boolean isAdmin) {
        Quiz q = findQuiz(id);
        offeringStaff.require(q.getPscId(), actorId, isAdmin);
        q.setTitle(req.getTitle());
        q.setDescription(req.getDescription());
        q.setDurationMinutes(req.getDurationMinutes());
        q.setAvailableFrom(req.getAvailableFrom());
        q.setAvailableUntil(req.getAvailableUntil());
        q.setShuffleQuestions(req.isShuffleQuestions());
        q.setShuffleOptions(req.isShuffleOptions());
        return toQuizResponse(quizRepository.save(q));
    }

    public void deleteQuiz(UUID id, UUID actorId, boolean isAdmin) {
        offeringStaff.require(findQuiz(id).getPscId(), actorId, isAdmin);
        quizRepository.deleteById(id);
    }

    // ── Quiz Questions ────────────────────────────────────────────────────────

    public QuizQuestionResponse addQuestion(UUID quizId, CreateQuizQuestionRequest req,
                                            UUID actorId, boolean isAdmin) {
        Quiz quiz = findQuiz(quizId);
        offeringStaff.require(quiz.getPscId(), actorId, isAdmin);
        validateQuestion(req.getType(), req.getOptions(), req.getCorrectAnswer());

        QuizQuestion qq = new QuizQuestion();
        qq.setQuizId(quizId);
        qq.setQuestionText(req.getQuestionText());
        qq.setType(req.getType());
        qq.setOptions(req.getOptions());
        qq.setCorrectAnswer(req.getCorrectAnswer());
        qq.setMarks(req.getMarks());
        qq.setOrderIndex(req.getOrderIndex());
        qq.setExplanation(req.getExplanation());

        QuizQuestion saved = questionRepository.save(qq);

        // Update cached total_marks
        quiz.setTotalMarks(quiz.getTotalMarks().add(req.getMarks()));
        quizRepository.save(quiz);

        return toQuestionResponse(saved, true);
    }

    public QuizQuestionResponse updateQuestion(UUID questionId, UpdateQuizQuestionRequest req,
                                               UUID actorId, boolean isAdmin) {
        QuizQuestion qq = findQuestion(questionId);
        offeringStaff.require(findQuiz(qq.getQuizId()).getPscId(), actorId, isAdmin);
        validateQuestion(req.getType(), req.getOptions(), req.getCorrectAnswer());

        BigDecimal oldMarks = qq.getMarks();

        qq.setQuestionText(req.getQuestionText());
        qq.setType(req.getType());
        qq.setOptions(req.getOptions());
        qq.setCorrectAnswer(req.getCorrectAnswer());
        qq.setMarks(req.getMarks());
        qq.setOrderIndex(req.getOrderIndex());
        qq.setExplanation(req.getExplanation());

        QuizQuestion saved = questionRepository.save(qq);

        // Update cached total_marks if marks changed
        if (oldMarks.compareTo(req.getMarks()) != 0) {
            Quiz quiz = findQuiz(qq.getQuizId());
            quiz.setTotalMarks(quiz.getTotalMarks().subtract(oldMarks).add(req.getMarks()));
            quizRepository.save(quiz);
        }

        return toQuestionResponse(saved, true);
    }

    public void deleteQuestion(UUID questionId, UUID actorId, boolean isAdmin) {
        QuizQuestion qq = findQuestion(questionId);
        Quiz quiz = findQuiz(qq.getQuizId());
        offeringStaff.require(quiz.getPscId(), actorId, isAdmin);

        questionRepository.deleteById(questionId);

        quiz.setTotalMarks(quiz.getTotalMarks().subtract(qq.getMarks()).max(BigDecimal.ZERO));
        quizRepository.save(quiz);
    }

    @Transactional(readOnly = true)
    /**
     * Correct answers are shown only to admins and to staff of this quiz's
     * offering — not to every user with a staff role, which let any teacher
     * read the answer key of any course's quiz.
     */
    public List<QuizQuestionResponse> listQuestions(UUID quizId, UUID userId, boolean isAdmin,
                                                    boolean hasStaffRole) {
        boolean includeCorrectAnswers = isAdmin
                || (hasStaffRole && offeringStaff.isStaff(findQuiz(quizId).getPscId(), userId));
        return questionRepository.findAllByQuizIdOrderByOrderIndexAsc(quizId)
                .stream().map(qq -> toQuestionResponse(qq, includeCorrectAnswers)).toList();
    }

    // ── Quiz Submissions ──────────────────────────────────────────────────────

    public QuizSubmissionResponse startQuiz(UUID quizId, UUID studentId) {
        Quiz quiz = findQuiz(quizId);

        if (!quizRepository.isStudentEnrolled(quizId, studentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not enrolled in this course offering");
        }

        LocalDateTime now = LocalDateTime.now();
        if (quiz.getAvailableFrom() != null && now.isBefore(quiz.getAvailableFrom())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Quiz is not yet available");
        }
        if (quiz.getAvailableUntil() != null && now.isAfter(quiz.getAvailableUntil())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Quiz availability window has closed");
        }

        Optional<QuizSubmission> existing = quizSubmissionRepository.findByQuizIdAndStudentId(quizId, studentId);
        if (existing.isPresent()) {
            return toQuizSubmissionResponse(existing.get());
        }

        // An empty quiz would start, submit and auto-grade to 0/0 — a silent
        // zero for the student and no way for them to tell it was our fault.
        if (questionRepository.countByQuizId(quizId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This quiz has no questions yet — ask your instructor to publish them");
        }

        QuizSubmission sub = new QuizSubmission();
        sub.setQuizId(quizId);
        sub.setStudentId(studentId);
        sub.setAnswers(new HashMap<>());

        return toQuizSubmissionResponse(quizSubmissionRepository.save(sub));
    }

    public QuizSubmissionResponse saveAnswers(UUID submissionId, UUID studentId,
                                              SubmitQuizAnswersRequest req) {
        QuizSubmission sub = findQuizSubmission(submissionId);
        requireOwnership(sub.getStudentId(), studentId);

        if (sub.getSubmittedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz already submitted");
        }
        if (isTimeUp(findQuiz(sub.getQuizId()), sub)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Time is up — this attempt can no longer be edited");
        }

        sub.setAnswers(req.getAnswers());
        return toQuizSubmissionResponse(quizSubmissionRepository.save(sub));
    }

    /**
     * Whether the attempt has run past its time limit. The countdown in the
     * browser is a convenience only — nothing stops a student from leaving the
     * tab open, so the deadline is decided here.
     */
    private boolean isTimeUp(Quiz quiz, QuizSubmission sub) {
        return isTimeUp(quiz, sub, Duration.ZERO);
    }

    private boolean isTimeUp(Quiz quiz, QuizSubmission sub, Duration grace) {
        if (quiz.getDurationMinutes() == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(deadlineOf(quiz, sub).plus(grace));
    }

    private static LocalDateTime deadlineOf(Quiz quiz, QuizSubmission sub) {
        return sub.getStartedAt().plusMinutes(quiz.getDurationMinutes());
    }

    /**
     * Seconds left on a timed, unsubmitted attempt; null otherwise.
     *
     * <p>Computed here and sent as a duration so the browser's countdown never
     * compares a server timestamp against its own clock — which a timezone or
     * clock difference between the two turns into an attempt that looks expired
     * the moment it starts.
     */
    private Long remainingSeconds(QuizSubmission sub) {
        if (sub.getSubmittedAt() != null) {
            return null;
        }
        Quiz quiz = quizRepository.findById(sub.getQuizId()).orElse(null);
        if (quiz == null || quiz.getDurationMinutes() == null) {
            return null;
        }
        long left = Duration.between(LocalDateTime.now(), deadlineOf(quiz, sub)).getSeconds();
        return Math.max(0, left);
    }

    /**
     * Submits and grades an attempt.
     *
     * <p>The final answers travel with the submit call rather than in a
     * separate save first. As two calls, an auto-submit fired by the timer at
     * 0:00 reached the save after the deadline, was refused with "Time is up",
     * and never reached the submit — the attempt stayed open and the answers
     * were lost. Answers arriving within {@link #SUBMIT_GRACE} of the deadline
     * are accepted to absorb that network delay; later ones are ignored and the
     * last saved answers are graded.
     *
     * @param finalAnswers the answers to grade, or null to grade those saved
     */
    public QuizSubmissionResponse submitQuiz(UUID submissionId, UUID studentId,
                                             Map<String, List<String>> finalAnswers) {
        QuizSubmission sub = findQuizSubmission(submissionId);
        requireOwnership(sub.getStudentId(), studentId);

        if (sub.getSubmittedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz already submitted");
        }

        Quiz quiz = findQuiz(sub.getQuizId());

        if (finalAnswers != null) {
            if (isTimeUp(quiz, sub, SUBMIT_GRACE)) {
                log.info("Ignoring answers sent after the deadline for quiz submission {}", submissionId);
            } else {
                sub.setAnswers(finalAnswers);
            }
        }

        BigDecimal score = autoGrade(sub.getQuizId(), sub.getAnswers());
        sub.setScore(score);
        sub.setAutoGraded(true);
        sub.setSubmittedAt(LocalDateTime.now());

        QuizSubmission saved = quizSubmissionRepository.save(sub);

        try {
            AssessmentContextView ctx = quizRepository.findEventContext(sub.getQuizId(), studentId);
            kafkaEventPublisher.publishAssessmentEvent(AssessmentEvent.builder()
                    .action(AssessmentEvent.Action.QUIZ_SUBMITTED)
                    .studentId(studentId)
                    .studentEmail(ctx.getStudentEmail())
                    .studentName(ctx.getStudentName())
                    .assessmentId(sub.getQuizId())
                    .assessmentTitle(quiz.getTitle())
                    .courseCode(ctx.getCourseCode())
                    .courseName(ctx.getCourseName())
                    .marksObtained(score.doubleValue())
                    .totalMarks(quiz.getTotalMarks().doubleValue())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish QUIZ_SUBMITTED event: {}", e.getMessage());
        }

        return toQuizSubmissionResponse(saved);
    }

    @Transactional(readOnly = true)
    public QuizSubmissionResponse getQuizSubmission(UUID submissionId, UUID actorId, boolean isAdmin) {
        QuizSubmission sub = findQuizSubmission(submissionId);
        offeringStaff.require(findQuiz(sub.getQuizId()).getPscId(), actorId, isAdmin);
        return toQuizSubmissionResponse(sub, submitters(List.of(sub.getStudentId())).get(sub.getStudentId()));
    }

    @Transactional(readOnly = true)
    public List<QuizSubmissionResponse> listQuizSubmissions(UUID quizId, UUID actorId, boolean isAdmin) {
        offeringStaff.require(findQuiz(quizId).getPscId(), actorId, isAdmin);
        List<QuizSubmission> subs = quizSubmissionRepository.findAllByQuizId(quizId);
        Map<UUID, SubmitterView> who = submitters(subs.stream().map(QuizSubmission::getStudentId).toList());
        return subs.stream().map(sub -> toQuizSubmissionResponse(sub, who.get(sub.getStudentId()))).toList();
    }

    @Transactional(readOnly = true)
    public QuizSubmissionResponse getMyQuizSubmission(UUID quizId, UUID studentId) {
        return quizSubmissionRepository.findByQuizIdAndStudentId(quizId, studentId)
                .map(this::toQuizSubmissionResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz submission not found"));
    }

    // ── CLO Mappings: Assignments ─────────────────────────────────────────────

    public CloMappingResponse addAssignmentCloMapping(UUID assignmentId, CloMappingRequest req,
                                                      UUID actorId, boolean isAdmin) {
        Assignment assignment = findAssignment(assignmentId);
        offeringStaff.require(assignment.getPscId(), actorId, isAdmin);

        if (assignmentCloMappingRepository.existsById_AssignmentIdAndId_CloId(assignmentId, req.getCloId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CLO mapping already exists");
        }

        AssignmentCloMapping mapping = AssignmentCloMapping.of(assignment, req.getCloId(), req.getWeight());
        return toAssignmentCloMappingResponse(assignmentCloMappingRepository.save(mapping));
    }

    public void removeAssignmentCloMapping(UUID assignmentId, UUID cloId, UUID actorId, boolean isAdmin) {
        offeringStaff.require(findAssignment(assignmentId).getPscId(), actorId, isAdmin);
        if (!assignmentCloMappingRepository.existsById_AssignmentIdAndId_CloId(assignmentId, cloId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CLO mapping not found");
        }
        assignmentCloMappingRepository.deleteByAssignmentIdAndCloId(assignmentId, cloId);
    }

    @Transactional(readOnly = true)
    public List<CloMappingResponse> listAssignmentCloMappings(UUID assignmentId) {
        return assignmentCloMappingRepository.findAllById_AssignmentId(assignmentId)
                .stream().map(this::toAssignmentCloMappingResponse).toList();
    }

    // ── CLO Mappings: Quizzes ─────────────────────────────────────────────────

    public CloMappingResponse addQuizCloMapping(UUID quizId, CloMappingRequest req,
                                                UUID actorId, boolean isAdmin) {
        Quiz quiz = findQuiz(quizId);
        offeringStaff.require(quiz.getPscId(), actorId, isAdmin);

        if (quizCloMappingRepository.existsById_QuizIdAndId_CloId(quizId, req.getCloId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CLO mapping already exists");
        }

        QuizCloMapping mapping = QuizCloMapping.of(quiz, req.getCloId(), req.getWeight());
        return toQuizCloMappingResponse(quizCloMappingRepository.save(mapping));
    }

    public void removeQuizCloMapping(UUID quizId, UUID cloId, UUID actorId, boolean isAdmin) {
        offeringStaff.require(findQuiz(quizId).getPscId(), actorId, isAdmin);
        if (!quizCloMappingRepository.existsById_QuizIdAndId_CloId(quizId, cloId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CLO mapping not found");
        }
        quizCloMappingRepository.deleteByQuizIdAndCloId(quizId, cloId);
    }

    @Transactional(readOnly = true)
    public List<CloMappingResponse> listQuizCloMappings(UUID quizId) {
        return quizCloMappingRepository.findAllById_QuizId(quizId)
                .stream().map(this::toQuizCloMappingResponse).toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Assignment findAssignment(UUID id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
    }

    private AssignmentSubmission findSubmission(UUID id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
    }

    private Quiz findQuiz(UUID id) {
        return quizRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found"));
    }

    private QuizQuestion findQuestion(UUID id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
    }

    private QuizSubmission findQuizSubmission(UUID id) {
        return quizSubmissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz submission not found"));
    }

    private void requireOwnership(UUID ownerId, UUID requestorId) {
        if (!ownerId.equals(requestorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private void validateSubmissionContent(String submissionType, SubmitAssignmentRequest req) {
        boolean hasFile = req.getFileKey() != null && !req.getFileKey().isBlank();
        boolean hasText = req.getTextContent() != null && !req.getTextContent().isBlank();

        if (!hasFile && !hasText) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Submission must include file or text content");
        }
        if ("FILE".equals(submissionType) && !hasFile) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This assignment requires a file submission");
        }
        if ("TEXT".equals(submissionType) && !hasText) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This assignment requires text submission");
        }
    }

    /**
     * Reduces the marks a late submission earns by the assignment's penalty.
     *
     * <p>The teacher grades the work on its merits; the deduction is the
     * system's job. Without this the {@code latePenaltyPercent} the teacher
     * configured is collected, displayed to students, and then quietly ignored.
     */
    private BigDecimal applyLatePenalty(Assignment assignment, AssignmentSubmission sub,
                                        BigDecimal rawMarks) {
        BigDecimal penaltyPercent = assignment.getLatePenaltyPercent();
        // Lateness is read from the timestamps, not the status: grading
        // overwrites the status with GRADED, so a re-grade would otherwise
        // hand back the marks the penalty took off.
        boolean wasLate = sub.getSubmittedAt() != null
                && sub.getSubmittedAt().isAfter(assignment.getDueDate());
        if (!wasLate || penaltyPercent == null || penaltyPercent.signum() <= 0) {
            return rawMarks;
        }
        BigDecimal retained = BigDecimal.ONE.subtract(
                penaltyPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        return rawMarks.multiply(retained)
                .setScale(2, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);
    }

    /**
     * Rejects a question no student could answer correctly.
     *
     * <p>Only the MCQ answer count used to be checked, so correct answers that
     * named no option were stored — the question builder was sending its own
     * internal row keys instead of option ids — and every student scored zero
     * on the question with nothing to show why.
     */
    private void validateQuestion(String type, List<Map<String, Object>> options,
                                  List<String> correctAnswer) {
        Set<String> optionIds = new HashSet<>();
        for (Map<String, Object> option : options) {
            Object id = option.get("id");
            Object text = option.get("text");
            if (!(id instanceof String s) || s.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Every option needs an id");
            }
            if (!(text instanceof String t) || t.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Every option needs some text");
            }
            if (!optionIds.add(s)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Two options share the id \"" + s + "\"");
            }
        }
        if (!optionIds.containsAll(correctAnswer)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The correct answer must be one of the question's options");
        }
        if (new HashSet<>(correctAnswer).size() != correctAnswer.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The same option is marked correct twice");
        }
        if ("MCQ".equals(type) && correctAnswer.size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A multiple-choice question must have exactly one correct answer");
        }
    }

    /**
     * Auto-grades a quiz submission: compares each question's correct answer to
     * the student's selected options using order-insensitive set equality.
     */
    private BigDecimal autoGrade(UUID quizId, Map<String, List<String>> answers) {
        List<QuizQuestion> questions = questionRepository.findAllByQuizIdOrderByOrderIndexAsc(quizId);
        BigDecimal score = BigDecimal.ZERO;
        for (QuizQuestion qq : questions) {
            List<String> selected = answers.getOrDefault(qq.getId().toString(), List.of());
            List<String> correct  = qq.getCorrectAnswer();
            if (new HashSet<>(selected).equals(new HashSet<>(correct))) {
                score = score.add(qq.getMarks());
            }
        }
        return score;
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private AssignmentResponse toAssignmentResponse(Assignment a) {
        return AssignmentResponse.builder()
                .id(a.getId())
                .pscId(a.getPscId())
                .createdBy(a.getCreatedBy())
                .title(a.getTitle())
                .description(a.getDescription())
                .submissionType(a.getSubmissionType())
                .totalMarks(a.getTotalMarks())
                .dueDate(a.getDueDate())
                .allowLateSubmission(a.isAllowLateSubmission())
                .latePenaltyPercent(a.getLatePenaltyPercent())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    /**
     * Staff see who submitted by name and roll number, not a bare UUID.
     * Empty input returns an empty map: {@code IN ()} is invalid SQL.
     */
    private Map<UUID, SubmitterView> submitters(Collection<UUID> studentIds) {
        if (studentIds.isEmpty()) return Map.of();
        return submissionRepository.findSubmitters(studentIds).stream()
                .collect(Collectors.toMap(v -> UUID.fromString(v.getStudentId()), v -> v));
    }

    private AssignmentSubmissionResponse toSubmissionResponse(AssignmentSubmission s) {
        return toSubmissionResponse(s, null);
    }

    private AssignmentSubmissionResponse toSubmissionResponse(AssignmentSubmission s, SubmitterView who) {
        return AssignmentSubmissionResponse.builder()
                .id(s.getId())
                .assignmentId(s.getAssignmentId())
                .studentId(s.getStudentId())
                .studentName(who != null ? who.getStudentName() : null)
                .studentEmail(who != null ? who.getStudentEmail() : null)
                .studentNumber(who != null ? who.getStudentNumber() : null)
                .status(s.getStatus())
                .textContent(s.getTextContent())
                .fileKey(s.getFileKey())
                .fileName(s.getFileName())
                .fileSize(s.getFileSize())
                .submittedAt(s.getSubmittedAt())
                .marksObtained(s.getMarksObtained())
                .feedback(s.getFeedback())
                .gradedBy(s.getGradedBy())
                .gradedAt(s.getGradedAt())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private QuizResponse toQuizResponse(Quiz q) {
        return QuizResponse.builder()
                .id(q.getId())
                .pscId(q.getPscId())
                .createdBy(q.getCreatedBy())
                .title(q.getTitle())
                .description(q.getDescription())
                .durationMinutes(q.getDurationMinutes())
                .totalMarks(q.getTotalMarks())
                .availableFrom(q.getAvailableFrom())
                .availableUntil(q.getAvailableUntil())
                .shuffleQuestions(q.isShuffleQuestions())
                .shuffleOptions(q.isShuffleOptions())
                .createdAt(q.getCreatedAt())
                .updatedAt(q.getUpdatedAt())
                .build();
    }

    private QuizQuestionResponse toQuestionResponse(QuizQuestion qq, boolean includeCorrectAnswer) {
        return QuizQuestionResponse.builder()
                .id(qq.getId())
                .quizId(qq.getQuizId())
                .questionText(qq.getQuestionText())
                .type(qq.getType())
                .options(qq.getOptions())
                .correctAnswer(includeCorrectAnswer ? qq.getCorrectAnswer() : null)
                .marks(qq.getMarks())
                .orderIndex(qq.getOrderIndex())
                .explanation(qq.getExplanation())
                .createdAt(qq.getCreatedAt())
                .updatedAt(qq.getUpdatedAt())
                .build();
    }

    private QuizSubmissionResponse toQuizSubmissionResponse(QuizSubmission s) {
        return toQuizSubmissionResponse(s, null);
    }

    private QuizSubmissionResponse toQuizSubmissionResponse(QuizSubmission s, SubmitterView who) {
        return QuizSubmissionResponse.builder()
                .id(s.getId())
                .quizId(s.getQuizId())
                .studentId(s.getStudentId())
                .studentName(who != null ? who.getStudentName() : null)
                .studentEmail(who != null ? who.getStudentEmail() : null)
                .studentNumber(who != null ? who.getStudentNumber() : null)
                .answers(s.getAnswers())
                .startedAt(s.getStartedAt())
                .remainingSeconds(remainingSeconds(s))
                .submittedAt(s.getSubmittedAt())
                .score(s.getScore())
                .autoGraded(s.isAutoGraded())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private CloMappingResponse toAssignmentCloMappingResponse(AssignmentCloMapping m) {
        return CloMappingResponse.builder()
                .assessmentId(m.getId().getAssignmentId())
                .cloId(m.getId().getCloId())
                .weight(m.getWeight())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private CloMappingResponse toQuizCloMappingResponse(QuizCloMapping m) {
        return CloMappingResponse.builder()
                .assessmentId(m.getId().getQuizId())
                .cloId(m.getId().getCloId())
                .weight(m.getWeight())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
