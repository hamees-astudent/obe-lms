package com.lms.modules.assessment;

import com.lms.infrastructure.messaging.KafkaEventPublisher;
import com.lms.shared.OfferingStaff;
import com.lms.modules.assessment.dto.CreateQuizQuestionRequest;
import com.lms.modules.assessment.dto.GradeSubmissionRequest;
import com.lms.modules.assessment.dto.SubmitAssignmentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

/**
 * Covers the assessment rules that were configurable but unenforced: quiz
 * attempts by non-enrolled students, quizzes with no questions, and the late
 * penalty that assignments collected but never applied.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssessmentServiceTest {

    @Mock private AssignmentRepository           assignmentRepository;
    @Mock private AssignmentSubmissionRepository submissionRepository;
    @Mock private QuizRepository                 quizRepository;
    @Mock private QuizQuestionRepository         questionRepository;
    @Mock private QuizSubmissionRepository       quizSubmissionRepository;
    @Mock private AssignmentCloMappingRepository assignmentCloMappingRepository;
    @Mock private QuizCloMappingRepository       quizCloMappingRepository;
    @Mock private KafkaEventPublisher            kafkaEventPublisher;
    @Mock private OfferingStaff                  offeringStaff;

    private AssessmentService service;

    private final UUID quizId       = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID submissionId = UUID.randomUUID();
    private final UUID studentId    = UUID.randomUUID();
    private final UUID teacherId    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AssessmentService(assignmentRepository, submissionRepository,
                quizRepository, questionRepository, quizSubmissionRepository,
                assignmentCloMappingRepository, quizCloMappingRepository, kafkaEventPublisher,
                offeringStaff);

        // Real admin/403 logic; only the database lookup is stubbed.
        doCallRealMethod().when(offeringStaff).require(any(), any(), anyBoolean());
        when(offeringStaff.isStaff(any(), any())).thenReturn(true);

        when(quizRepository.findById(quizId)).thenReturn(Optional.of(quiz()));
        when(quizRepository.isStudentEnrolled(any(), any())).thenReturn(true);
        when(questionRepository.countByQuizId(quizId)).thenReturn(3L);
        when(quizSubmissionRepository.findByQuizIdAndStudentId(any(), any()))
                .thenReturn(Optional.empty());
        when(quizSubmissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(assignmentRepository.isStudentEnrolled(any(), any())).thenReturn(true);
        when(submissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Quiz attempts ────────────────────────────────────────────────────────

    @Test
    @DisplayName("a student not enrolled in the offering cannot start the quiz")
    void nonEnrolledStudentCannotStartQuiz() {
        when(quizRepository.isStudentEnrolled(quizId, studentId)).thenReturn(false);

        assertThatThrownBy(() -> service.startQuiz(quizId, studentId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a quiz with no questions cannot be started")
    void emptyQuizCannotBeStarted() {
        when(questionRepository.countByQuizId(quizId)).thenReturn(0L);

        assertThatThrownBy(() -> service.startQuiz(quizId, studentId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("an enrolled student starting a populated quiz gets a submission")
    void enrolledStudentStartsQuiz() {
        var response = service.startQuiz(quizId, studentId);

        assertThat(response.getQuizId()).isEqualTo(quizId);
        assertThat(response.getStudentId()).isEqualTo(studentId);
        assertThat(response.getSubmittedAt()).isNull();
    }

    // ── Assignment submissions ───────────────────────────────────────────────

    @Test
    @DisplayName("a student not enrolled in the offering cannot submit")
    void nonEnrolledStudentCannotSubmit() {
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(BigDecimal.valueOf(20))));
        when(assignmentRepository.isStudentEnrolled(assignmentId, studentId)).thenReturn(false);

        var req = new SubmitAssignmentRequest();
        req.setTextContent("my answer");

        assertThatThrownBy(() -> service.submitAssignment(assignmentId, studentId, req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── Late penalty ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a late submission has the configured penalty deducted")
    void latePenaltyIsApplied() {
        Assignment assignment = assignment(BigDecimal.valueOf(20));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findById(submissionId))
                .thenReturn(Optional.of(submission(assignment.getDueDate().plusHours(3))));

        var graded = service.gradeSubmission(submissionId, teacherId, false,
                gradeRequest(BigDecimal.valueOf(20)));

        // 20 marks less a 25% late penalty
        assertThat(graded.getMarksObtained()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("an on-time submission keeps every mark awarded")
    void onTimeSubmissionIsNotPenalised() {
        Assignment assignment = assignment(BigDecimal.valueOf(20));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findById(submissionId))
                .thenReturn(Optional.of(submission(assignment.getDueDate().minusHours(3))));

        var graded = service.gradeSubmission(submissionId, teacherId, false,
                gradeRequest(BigDecimal.valueOf(20)));

        assertThat(graded.getMarksObtained()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("re-grading a late submission applies the penalty again, not on top of itself")
    void regradeIsIdempotent() {
        Assignment assignment = assignment(BigDecimal.valueOf(20));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        AssignmentSubmission sub = submission(assignment.getDueDate().plusHours(3));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(sub));

        service.gradeSubmission(submissionId, teacherId, false, gradeRequest(BigDecimal.valueOf(20)));
        var second = service.gradeSubmission(submissionId, teacherId, false,
                gradeRequest(BigDecimal.valueOf(20)));

        assertThat(second.getMarksObtained()).isEqualByComparingTo("15.00");
    }

    // ── Quiz submission timing ───────────────────────────────────────────────

    @Test
    @DisplayName("submit grades the answers sent with it, not only those saved earlier")
    void submitGradesAnswersSentWithIt() {
        timedQuiz(10);
        var sub = attempt(LocalDateTime.now().minusMinutes(2));
        when(quizSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(sub));

        var result = service.submitQuiz(submissionId, studentId,
                Map.of(questionId.toString(), List.of("a")));

        assertThat(result.getScore()).isEqualByComparingTo("5");
        assertThat(result.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("an auto-submit arriving just after the deadline still counts its answers")
    void answersWithinGraceAreAccepted() {
        timedQuiz(10);
        var sub = attempt(LocalDateTime.now().minusMinutes(10).minusSeconds(5));
        when(quizSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(sub));

        var result = service.submitQuiz(submissionId, studentId,
                Map.of(questionId.toString(), List.of("a")));

        assertThat(result.getScore()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("answers sent well after the deadline are ignored; the saved ones are graded")
    void answersAfterGraceAreIgnored() {
        timedQuiz(10);
        var sub = attempt(LocalDateTime.now().minusMinutes(20));
        sub.setAnswers(Map.of(questionId.toString(), List.of("b")));
        when(quizSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(sub));

        var result = service.submitQuiz(submissionId, studentId,
                Map.of(questionId.toString(), List.of("a")));

        assertThat(result.getScore()).isEqualByComparingTo("0");
        assertThat(result.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("the time left is computed on the server, not from the browser's clock")
    void remainingSecondsComeFromServer() {
        timedQuiz(10);
        var sub = attempt(LocalDateTime.now().minusMinutes(4));
        when(quizSubmissionRepository.findByQuizIdAndStudentId(quizId, studentId))
                .thenReturn(Optional.of(sub));

        var response = service.getMyQuizSubmission(quizId, studentId);

        assertThat(response.getRemainingSeconds()).isBetween(355L, 360L);
    }

    // ── Question validation ──────────────────────────────────────────────────

    @Test
    @DisplayName("a correct answer that names no option is rejected")
    void correctAnswerMustBeAnOption() {
        // What the question builder used to send: its internal row key, not "a"/"b".
        var req = question("MCQ", List.of("3f2a9c1e-row-key"));

        assertBadRequest(() -> service.addQuestion(quizId, req, teacherId, false));
    }

    @Test
    @DisplayName("duplicate option ids are rejected")
    void optionIdsMustBeUnique() {
        var req = question("MCQ", List.of("a"));
        req.setOptions(List.of(Map.of("id", "a", "text", "Yes"), Map.of("id", "a", "text", "No")));

        assertBadRequest(() -> service.addQuestion(quizId, req, teacherId, false));
    }

    @Test
    @DisplayName("an option with blank text is rejected")
    void optionTextIsRequired() {
        var req = question("MSQ", List.of("a"));
        req.setOptions(List.of(Map.of("id", "a", "text", "Yes"), Map.of("id", "b", "text", " ")));

        assertBadRequest(() -> service.addQuestion(quizId, req, teacherId, false));
    }

    @Test
    @DisplayName("a well-formed question is saved")
    void validQuestionIsSaved() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var saved = service.addQuestion(quizId, question("MSQ", List.of("a", "b")), teacherId, false);

        assertThat(saved.getCorrectAnswer()).containsExactly("a", "b");
    }

    // ── Offering ownership ───────────────────────────────────────────────────

    @Test
    @DisplayName("a teacher who does not run the offering cannot add a quiz question")
    void nonStaffCannotAddQuestion() {
        when(offeringStaff.isStaff(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.addQuestion(quizId, question("MCQ", List.of("a")), teacherId, false))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a teacher who does not run the offering cannot grade its submissions")
    void nonStaffCannotGrade() {
        when(offeringStaff.isStaff(any(), any())).thenReturn(false);
        when(submissionRepository.findById(submissionId))
                .thenReturn(Optional.of(submission(LocalDateTime.now())));
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(BigDecimal.valueOf(20))));

        assertThatThrownBy(() -> service.gradeSubmission(submissionId, teacherId, false,
                gradeRequest(BigDecimal.TEN)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("an admin may add questions to any offering's quiz")
    void adminBypassesOwnership() {
        when(offeringStaff.isStaff(any(), any())).thenReturn(false);
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var saved = service.addQuestion(quizId, question("MCQ", List.of("a")), teacherId, true);

        assertThat(saved.getCorrectAnswer()).containsExactly("a");
    }

    @Test
    @DisplayName("the answer key is shown to the course's staff, not to other teachers")
    void answerKeyOnlyForOwnStaff() {
        var q = new QuizQuestion();
        ReflectionTestUtils.setField(q, "id", UUID.randomUUID());
        q.setQuizId(quizId);
        q.setOptions(List.of(Map.of("id", "a", "text", "2")));
        q.setCorrectAnswer(List.of("a"));
        q.setMarks(BigDecimal.ONE);
        when(questionRepository.findAllByQuizIdOrderByOrderIndexAsc(quizId)).thenReturn(List.of(q));

        when(offeringStaff.isStaff(any(), any())).thenReturn(false);
        assertThat(service.listQuestions(quizId, teacherId, false, true).get(0).getCorrectAnswer()).isNull();

        when(offeringStaff.isStaff(any(), any())).thenReturn(true);
        assertThat(service.listQuestions(quizId, teacherId, false, true).get(0).getCorrectAnswer())
                .containsExactly("a");
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static CreateQuizQuestionRequest question(String type, List<String> correct) {
        var req = new CreateQuizQuestionRequest();
        req.setQuestionText("Which are prime?");
        req.setType(type);
        req.setOptions(List.of(Map.of("id", "a", "text", "2"), Map.of("id", "b", "text", "3"),
                Map.of("id", "c", "text", "4")));
        req.setCorrectAnswer(correct);
        req.setMarks(BigDecimal.ONE);
        return req;
    }

    private static void assertBadRequest(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private final UUID questionId = UUID.randomUUID();

    /** A quiz with a time limit and one 5-mark MCQ whose answer is "a". */
    private void timedQuiz(int minutes) {
        var q = quiz();
        q.setDurationMinutes(minutes);
        when(quizRepository.findById(quizId)).thenReturn(Optional.of(q));

        var question = new QuizQuestion();
        ReflectionTestUtils.setField(question, "id", questionId);
        question.setCorrectAnswer(List.of("a"));
        question.setMarks(BigDecimal.valueOf(5));
        when(questionRepository.findAllByQuizIdOrderByOrderIndexAsc(quizId))
                .thenReturn(List.of(question));
    }

    private QuizSubmission attempt(LocalDateTime startedAt) {
        var sub = new QuizSubmission();
        sub.setQuizId(quizId);
        sub.setStudentId(studentId);
        sub.setStartedAt(startedAt);
        sub.setAnswers(new HashMap<>());
        return sub;
    }

    private Quiz quiz() {
        var q = new Quiz();
        q.setTitle("Midterm");
        q.setTotalMarks(BigDecimal.TEN);
        return q;
    }

    private Assignment assignment(BigDecimal totalMarks) {
        var a = new Assignment();
        a.setTitle("Lab 3");
        a.setSubmissionType("TEXT");
        a.setTotalMarks(totalMarks);
        a.setDueDate(LocalDateTime.now().minusDays(1));
        a.setAllowLateSubmission(true);
        a.setLatePenaltyPercent(BigDecimal.valueOf(25));
        return a;
    }

    private AssignmentSubmission submission(LocalDateTime submittedAt) {
        var sub = new AssignmentSubmission();
        sub.setAssignmentId(assignmentId);
        sub.setStudentId(studentId);
        sub.setSubmittedAt(submittedAt);
        sub.setStatus(submittedAt.isAfter(LocalDateTime.now().minusDays(1)) ? "LATE" : "SUBMITTED");
        return sub;
    }

    private GradeSubmissionRequest gradeRequest(BigDecimal marks) {
        var req = new GradeSubmissionRequest();
        req.setMarksObtained(marks);
        req.setFeedback("Good work");
        return req;
    }
}
