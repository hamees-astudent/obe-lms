package com.lms.modules.assessment;

import com.lms.infrastructure.messaging.KafkaEventPublisher;
import com.lms.modules.assessment.dto.*;
import com.lms.shared.events.AssessmentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AssessmentService {

    private final AssignmentRepository             assignmentRepository;
    private final AssignmentSubmissionRepository   submissionRepository;
    private final QuizRepository                   quizRepository;
    private final QuizQuestionRepository           questionRepository;
    private final QuizSubmissionRepository         quizSubmissionRepository;
    private final AssignmentCloMappingRepository   assignmentCloMappingRepository;
    private final QuizCloMappingRepository         quizCloMappingRepository;
    private final KafkaEventPublisher              kafkaEventPublisher;

    // ── Assignments ───────────────────────────────────────────────────────────

    public AssignmentResponse createAssignment(UUID createdBy, CreateAssignmentRequest req) {
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
        return toAssignmentResponse(assignmentRepository.save(a));
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

    public AssignmentResponse updateAssignment(UUID id, UpdateAssignmentRequest req) {
        Assignment a = findAssignment(id);
        a.setTitle(req.getTitle());
        a.setDescription(req.getDescription());
        a.setTotalMarks(req.getTotalMarks());
        a.setDueDate(req.getDueDate());
        a.setAllowLateSubmission(req.isAllowLateSubmission());
        a.setLatePenaltyPercent(req.getLatePenaltyPercent());
        return toAssignmentResponse(assignmentRepository.save(a));
    }

    public void deleteAssignment(UUID id) {
        if (!assignmentRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found");
        }
        assignmentRepository.deleteById(id);
    }

    // ── Assignment Submissions ────────────────────────────────────────────────

    public AssignmentSubmissionResponse submitAssignment(UUID assignmentId, UUID studentId,
                                                         SubmitAssignmentRequest req) {
        Assignment assignment = findAssignment(assignmentId);

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
                                                         GradeSubmissionRequest req) {
        AssignmentSubmission sub = findSubmission(submissionId);
        Assignment assignment = findAssignment(sub.getAssignmentId());

        if (req.getMarksObtained().compareTo(assignment.getTotalMarks()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Marks obtained cannot exceed total marks (" + assignment.getTotalMarks() + ")");
        }

        sub.setMarksObtained(req.getMarksObtained());
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
                    .marksObtained(req.getMarksObtained().doubleValue())
                    .totalMarks(assignment.getTotalMarks().doubleValue())
                    .feedback(req.getFeedback())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish ASSIGNMENT_GRADED event: {}", e.getMessage());
        }

        return toSubmissionResponse(saved);
    }

    @Transactional(readOnly = true)
    public AssignmentSubmissionResponse getSubmission(UUID submissionId) {
        return toSubmissionResponse(findSubmission(submissionId));
    }

    @Transactional(readOnly = true)
    public List<AssignmentSubmissionResponse> listSubmissions(UUID assignmentId) {
        findAssignment(assignmentId); // verify exists
        return submissionRepository.findAllByAssignmentId(assignmentId)
                .stream().map(this::toSubmissionResponse).toList();
    }

    @Transactional(readOnly = true)
    public AssignmentSubmissionResponse getMySubmission(UUID assignmentId, UUID studentId) {
        return submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)
                .map(this::toSubmissionResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
    }

    // ── Quizzes ───────────────────────────────────────────────────────────────

    public QuizResponse createQuiz(UUID createdBy, CreateQuizRequest req) {
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
        return toQuizResponse(quizRepository.save(q));
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

    public QuizResponse updateQuiz(UUID id, UpdateQuizRequest req) {
        Quiz q = findQuiz(id);
        q.setTitle(req.getTitle());
        q.setDescription(req.getDescription());
        q.setDurationMinutes(req.getDurationMinutes());
        q.setAvailableFrom(req.getAvailableFrom());
        q.setAvailableUntil(req.getAvailableUntil());
        q.setShuffleQuestions(req.isShuffleQuestions());
        q.setShuffleOptions(req.isShuffleOptions());
        return toQuizResponse(quizRepository.save(q));
    }

    public void deleteQuiz(UUID id) {
        if (!quizRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found");
        }
        quizRepository.deleteById(id);
    }

    // ── Quiz Questions ────────────────────────────────────────────────────────

    public QuizQuestionResponse addQuestion(UUID quizId, CreateQuizQuestionRequest req) {
        Quiz quiz = findQuiz(quizId);
        validateQuestion(req.getType(), req.getCorrectAnswer());

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

    public QuizQuestionResponse updateQuestion(UUID questionId, UpdateQuizQuestionRequest req) {
        QuizQuestion qq = findQuestion(questionId);
        validateQuestion(req.getType(), req.getCorrectAnswer());

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

    public void deleteQuestion(UUID questionId) {
        QuizQuestion qq = findQuestion(questionId);
        Quiz quiz = findQuiz(qq.getQuizId());

        questionRepository.deleteById(questionId);

        quiz.setTotalMarks(quiz.getTotalMarks().subtract(qq.getMarks()).max(BigDecimal.ZERO));
        quizRepository.save(quiz);
    }

    @Transactional(readOnly = true)
    public List<QuizQuestionResponse> listQuestions(UUID quizId, boolean includeCorrectAnswers) {
        return questionRepository.findAllByQuizIdOrderByOrderIndexAsc(quizId)
                .stream().map(qq -> toQuestionResponse(qq, includeCorrectAnswers)).toList();
    }

    // ── Quiz Submissions ──────────────────────────────────────────────────────

    public QuizSubmissionResponse startQuiz(UUID quizId, UUID studentId) {
        Quiz quiz = findQuiz(quizId);

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

        sub.setAnswers(req.getAnswers());
        return toQuizSubmissionResponse(quizSubmissionRepository.save(sub));
    }

    public QuizSubmissionResponse submitQuiz(UUID submissionId, UUID studentId) {
        QuizSubmission sub = findQuizSubmission(submissionId);
        requireOwnership(sub.getStudentId(), studentId);

        if (sub.getSubmittedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz already submitted");
        }

        Quiz quiz = findQuiz(sub.getQuizId());

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
    public QuizSubmissionResponse getQuizSubmission(UUID submissionId) {
        return toQuizSubmissionResponse(findQuizSubmission(submissionId));
    }

    @Transactional(readOnly = true)
    public List<QuizSubmissionResponse> listQuizSubmissions(UUID quizId) {
        findQuiz(quizId); // verify exists
        return quizSubmissionRepository.findAllByQuizId(quizId)
                .stream().map(this::toQuizSubmissionResponse).toList();
    }

    @Transactional(readOnly = true)
    public QuizSubmissionResponse getMyQuizSubmission(UUID quizId, UUID studentId) {
        return quizSubmissionRepository.findByQuizIdAndStudentId(quizId, studentId)
                .map(this::toQuizSubmissionResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz submission not found"));
    }

    // ── CLO Mappings: Assignments ─────────────────────────────────────────────

    public CloMappingResponse addAssignmentCloMapping(UUID assignmentId, CloMappingRequest req) {
        Assignment assignment = findAssignment(assignmentId);

        if (assignmentCloMappingRepository.existsById_AssignmentIdAndId_CloId(assignmentId, req.getCloId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CLO mapping already exists");
        }

        AssignmentCloMapping mapping = AssignmentCloMapping.of(assignment, req.getCloId(), req.getWeight());
        return toAssignmentCloMappingResponse(assignmentCloMappingRepository.save(mapping));
    }

    public void removeAssignmentCloMapping(UUID assignmentId, UUID cloId) {
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

    public CloMappingResponse addQuizCloMapping(UUID quizId, CloMappingRequest req) {
        Quiz quiz = findQuiz(quizId);

        if (quizCloMappingRepository.existsById_QuizIdAndId_CloId(quizId, req.getCloId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CLO mapping already exists");
        }

        QuizCloMapping mapping = QuizCloMapping.of(quiz, req.getCloId(), req.getWeight());
        return toQuizCloMappingResponse(quizCloMappingRepository.save(mapping));
    }

    public void removeQuizCloMapping(UUID quizId, UUID cloId) {
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

    private void validateQuestion(String type, List<String> correctAnswer) {
        if ("MCQ".equals(type) && correctAnswer.size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "MCQ must have exactly one correct answer");
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

    private AssignmentSubmissionResponse toSubmissionResponse(AssignmentSubmission s) {
        return AssignmentSubmissionResponse.builder()
                .id(s.getId())
                .assignmentId(s.getAssignmentId())
                .studentId(s.getStudentId())
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
        return QuizSubmissionResponse.builder()
                .id(s.getId())
                .quizId(s.getQuizId())
                .studentId(s.getStudentId())
                .answers(s.getAnswers())
                .startedAt(s.getStartedAt())
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
