package com.lms.modules.assessment;

import com.lms.infrastructure.messaging.KafkaEventPublisher;
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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
                assignmentCloMappingRepository, quizCloMappingRepository, kafkaEventPublisher);

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

        var graded = service.gradeSubmission(submissionId, teacherId,
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

        var graded = service.gradeSubmission(submissionId, teacherId,
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

        service.gradeSubmission(submissionId, teacherId, gradeRequest(BigDecimal.valueOf(20)));
        var second = service.gradeSubmission(submissionId, teacherId,
                gradeRequest(BigDecimal.valueOf(20)));

        assertThat(second.getMarksObtained()).isEqualByComparingTo("15.00");
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

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
