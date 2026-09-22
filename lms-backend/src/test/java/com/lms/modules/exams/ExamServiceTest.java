package com.lms.modules.exams;

import com.lms.modules.exams.dto.QuestionMarkEntry;
import com.lms.shared.Role;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the gate every exam mark passes through. These rules are what stop a
 * misread scan or a mistyped entry becoming an academic record, so each one is
 * pinned by a test rather than left to review.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamServiceTest {

    @Mock private ExamRepository                   examRepo;
    @Mock private ExamQuestionRepository           questionRepo;
    @Mock private ExamQuestionCloMappingRepository cloMappingRepo;
    @Mock private ExamResultRepository             resultRepo;
    @Mock private ExamQuestionMarkRepository       questionMarkRepo;
    @Mock private ExamDataRepository               dataRepo;

    private ExamService service;

    private final UUID examId    = UUID.randomUUID();
    private final UUID pscId     = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();
    private final UUID q1Id      = UUID.randomUUID();
    private final UUID q2Id      = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ExamService(examRepo, questionRepo, cloMappingRepo,
                resultRepo, questionMarkRepo, dataRepo);

        when(dataRepo.isEnrolled(any(), any())).thenReturn(true);
        when(dataRepo.canManageOffering(any(), any())).thenReturn(true);
        when(questionRepo.findAllByExamIdOrderByOrderIndexAsc(examId))
                .thenReturn(List.of(question(q1Id, "1", "10", 1), question(q2Id, "2", "20", 2)));
        when(resultRepo.findByExamIdAndStudentId(any(), any())).thenReturn(Optional.empty());
        when(resultRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Marks are checked against the question's own maximum ─────────────────

    @Test
    @DisplayName("a mark above the question's maximum is rejected")
    void rejectsMarkAboveQuestionMaximum() {
        assertThatThrownBy(() -> service.writeResult(openExam(), studentId,
                List.of(entry(q1Id, "11"), entry(q2Id, "5")),
                teacherId, ExamResult.SOURCE_SCAN, UUID.randomUUID(), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("only out of 10");

        verify(resultRepo, never()).save(any());
    }

    @Test
    @DisplayName("a mark equal to the maximum is accepted")
    void acceptsFullMarks() {
        ExamResult result = service.writeResult(openExam(), studentId,
                List.of(entry(q1Id, "10"), entry(q2Id, "20")),
                teacherId, ExamResult.SOURCE_MANUAL, null, null);

        assertThat(result.getTotalObtained()).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("nothing is written when any row in the submission is invalid")
    void validatesWholeSubmissionBeforeWriting() {
        assertThatThrownBy(() -> service.writeResult(openExam(), studentId,
                List.of(entry(q1Id, "5"), entry(q2Id, "999")),
                teacherId, ExamResult.SOURCE_MANUAL, null, null))
                .isInstanceOf(ResponseStatusException.class);

        // The valid first row must not have been persisted on its own.
        verify(resultRepo, never()).save(any());
        verify(questionMarkRepo, never()).save(any());
    }

    @Test
    @DisplayName("an unattempted question contributes null, not zero, and is excluded from the total")
    void unattemptedQuestionIsNullNotZero() {
        ExamResult result = service.writeResult(openExam(), studentId,
                List.of(entry(q1Id, "7"), entry(q2Id, null)),
                teacherId, ExamResult.SOURCE_MANUAL, null, null);

        assertThat(result.getTotalObtained()).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("a question from another exam is rejected")
    void rejectsForeignQuestion() {
        assertThatThrownBy(() -> service.writeResult(openExam(), studentId,
                List.of(entry(UUID.randomUUID(), "5")),
                teacherId, ExamResult.SOURCE_MANUAL, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not belong to this exam");
    }

    @Test
    @DisplayName("the same question submitted twice is rejected")
    void rejectsDuplicateQuestion() {
        assertThatThrownBy(() -> service.writeResult(openExam(), studentId,
                List.of(entry(q1Id, "5"), entry(q1Id, "6")),
                teacherId, ExamResult.SOURCE_MANUAL, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("submitted twice");
    }

    // ── Exam and student state ───────────────────────────────────────────────

    @Test
    @DisplayName("marks cannot be recorded against a locked exam")
    void rejectsLockedExam() {
        Exam exam = openExam();
        exam.setStatus(Exam.STATUS_LOCKED);

        assertThatThrownBy(() -> service.writeResult(exam, studentId,
                List.of(entry(q1Id, "5")), teacherId, ExamResult.SOURCE_MANUAL, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("locked");
    }

    @Test
    @DisplayName("marks cannot be recorded against a draft exam")
    void rejectsDraftExam() {
        Exam exam = openExam();
        exam.setStatus(Exam.STATUS_DRAFT);

        assertThatThrownBy(() -> service.writeResult(exam, studentId,
                List.of(entry(q1Id, "5")), teacherId, ExamResult.SOURCE_MANUAL, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Open the exam");
    }

    @Test
    @DisplayName("marks cannot be recorded for a student who is not enrolled")
    void rejectsUnenrolledStudent() {
        when(dataRepo.isEnrolled(pscId, studentId)).thenReturn(false);

        assertThatThrownBy(() -> service.writeResult(openExam(), studentId,
                List.of(entry(q1Id, "5")), teacherId, ExamResult.SOURCE_MANUAL, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not enrolled");
    }

    @Test
    @DisplayName("re-recording a student's marks updates the existing result rather than adding one")
    void reScanUpdatesExistingResult() {
        ExamResult existing = new ExamResult();
        existing.setExamId(examId);
        existing.setStudentId(studentId);
        existing.setTotalObtained(new BigDecimal("12"));
        when(resultRepo.findByExamIdAndStudentId(examId, studentId)).thenReturn(Optional.of(existing));

        ExamResult result = service.writeResult(openExam(), studentId,
                List.of(entry(q1Id, "9"), entry(q2Id, "11")),
                teacherId, ExamResult.SOURCE_SCAN, UUID.randomUUID(), null);

        assertThat(result).isSameAs(existing);
        assertThat(result.getTotalObtained()).isEqualByComparingTo("20");
        // Old per-question rows must go, or a removed mark would linger.
        verify(questionMarkRepo).deleteAllByResultId(any());
    }

    // ── Offering-level authorisation ─────────────────────────────────────────

    @Test
    @DisplayName("a teacher who does not run the offering is refused")
    void refusesForeignOffering() {
        when(dataRepo.canManageOffering(pscId, teacherId)).thenReturn(false);

        assertThatThrownBy(() -> service.requireOfferingAccess(pscId, teacherId, Role.TEACHER))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("an admin may act on any offering")
    void adminBypassesOfferingCheck() {
        when(dataRepo.canManageOffering(pscId, teacherId)).thenReturn(false);

        service.requireOfferingAccess(pscId, teacherId, Role.ADMIN);

        verify(dataRepo, never()).canManageOffering(any(), any());
    }

    // ── Opening an exam ──────────────────────────────────────────────────────

    @Test
    @DisplayName("an exam whose questions do not add up to its total cannot be opened")
    void refusesToOpenWhenMarksDoNotAddUp() {
        Exam exam = openExam();
        exam.setStatus(Exam.STATUS_DRAFT);
        exam.setTotalMarks(new BigDecimal("50"));   // questions total 30
        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.changeStatus(examId, teacherId, Role.TEACHER, Exam.STATUS_OPEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("add up to 30");
    }

    @Test
    @DisplayName("an exam with no questions cannot be opened")
    void refusesToOpenWithoutQuestions() {
        Exam exam = openExam();
        exam.setStatus(Exam.STATUS_DRAFT);
        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(questionRepo.findAllByExamIdOrderByOrderIndexAsc(examId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.changeStatus(examId, teacherId, Role.TEACHER, Exam.STATUS_OPEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Add the exam's questions");
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private Exam openExam() {
        Exam exam = new Exam();
        setId(exam, examId);
        exam.setPscId(pscId);
        exam.setCreatedBy(teacherId);
        exam.setTitle("Midterm");
        exam.setExamType(Exam.TYPE_MIDTERM);
        exam.setExamDate(LocalDate.of(2026, 3, 12));
        exam.setTotalMarks(new BigDecimal("30"));
        exam.setStatus(Exam.STATUS_OPEN);
        return exam;
    }

    private ExamQuestion question(UUID id, String label, String max, int order) {
        ExamQuestion q = new ExamQuestion();
        setId(q, id);
        q.setExamId(examId);
        q.setQuestionNo(label);
        q.setMaxMarks(new BigDecimal(max));
        q.setOrderIndex(order);
        return q;
    }

    private QuestionMarkEntry entry(UUID questionId, String marks) {
        QuestionMarkEntry e = new QuestionMarkEntry();
        e.setQuestionId(questionId);
        e.setMarksObtained(marks == null ? null : new BigDecimal(marks));
        return e;
    }

    /** BaseEntity's id is provider-generated, so tests set it reflectively. */
    private static void setId(Object entity, UUID id) {
        try {
            var field = Class.forName("com.lms.shared.BaseEntity").getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
