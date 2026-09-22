package com.lms.modules.exams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.infrastructure.storage.StorageService;
import com.lms.modules.exams.ExamDataRepository.StudentRow;
import com.lms.modules.exams.MarksSheetExtractor.QuestionExpectation;
import com.lms.modules.exams.dto.*;
import com.lms.modules.exams.dto.ExtractedMarksSheet.ExtractedQuestionMark;
import com.lms.shared.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers what the scan flow does with an imperfect reading.
 *
 * <p>The theme throughout: a disagreement between the page and the system is
 * surfaced to the teacher, never silently resolved and never used to write a
 * mark on its own.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamScanServiceTest {

    @Mock private ExamScanRepository     scanRepo;
    @Mock private ExamRepository         examRepo;
    @Mock private ExamQuestionRepository questionRepo;
    @Mock private ExamResultRepository   resultRepo;
    @Mock private ExamDataRepository     dataRepo;
    @Mock private ExamService            examService;
    @Mock private StorageService         storageService;
    @Mock private ObjectProvider<MarksSheetExtractor> extractorProvider;

    private ExamScanService service;
    private StubExtractor   extractor;

    private final UUID examId    = UUID.randomUUID();
    private final UUID pscId     = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID q1Id      = UUID.randomUUID();
    private final UUID q2Id      = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        extractor = new StubExtractor();
        when(extractorProvider.getIfAvailable()).thenReturn(extractor);
        service = new ExamScanService(scanRepo, examRepo, questionRepo, resultRepo,
                dataRepo, examService, storageService, new ObjectMapper(), extractorProvider);

        when(storageService.download(any())).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(examService.findExamOrThrow(examId)).thenReturn(openExam());
        when(questionRepo.findAllByExamIdOrderByOrderIndexAsc(examId))
                .thenReturn(List.of(question(q1Id, "1", "10", 1), question(q2Id, "2", "20", 2)));
        when(scanRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(resultRepo.findByExamIdAndStudentId(any(), any())).thenReturn(Optional.empty());
        when(dataRepo.findRoster(pscId)).thenReturn(List.of(
                new StudentRow(studentId, "Ayesha Siddiqui", "B21-CS-045")));
        when(dataRepo.findOffering(pscId)).thenReturn(Optional.of(
                new ExamDataRepository.OfferingRow(pscId, UUID.randomUUID(), "CS-363",
                        "Software Engineering", teacherId, UUID.randomUUID())));
        when(dataRepo.findEnrolledStudentByRollNumber(pscId, "B21-CS-045"))
                .thenReturn(Optional.of(new StudentRow(studentId, "Ayesha Siddiqui", "B21-CS-045")));
    }

    // ── Question-label matching ──────────────────────────────────────────────

    @Test
    @DisplayName("a marker's 'Q1.' is matched to the exam's question '1'")
    void matchesLabelsAcrossPunctuation() {
        extractor.sheet = sheet(List.of(
                new ExtractedQuestionMark("Q1.", 8.0, 10.0),
                new ExtractedQuestionMark("Q2.", 15.0, 20.0)));

        ScanReviewResponse review = service.createScan(teacherId, Role.TEACHER, request());

        assertThat(review.getProposedMarks())
                .extracting(m -> m.getMarksObtained() == null ? null : m.getMarksObtained().intValue())
                .containsExactly(8, 15);
        assertThat(review.getProposedTotal()).isEqualByComparingTo("23");
    }

    @Test
    @DisplayName("a question with no row on the page is proposed as unread, not zero")
    void missingQuestionIsUnreadNotZero() {
        extractor.sheet = sheet(List.of(new ExtractedQuestionMark("1", 8.0, 10.0)));

        ScanReviewResponse review = service.createScan(teacherId, Role.TEACHER, request());

        ScanReviewResponse.ProposedMark second = review.getProposedMarks().get(1);
        assertThat(second.getMarksObtained()).isNull();
        assertThat(second.isUnread()).isTrue();
        assertThat(review.getWarnings())
                .anySatisfy(w -> assertThat(w).containsIgnoringCase("No mark was found for question 2"));
    }

    @Test
    @DisplayName("a blank cell stays null rather than becoming a zero")
    void blankCellStaysNull() {
        extractor.sheet = sheet(List.of(
                new ExtractedQuestionMark("1", null, 10.0),
                new ExtractedQuestionMark("2", 15.0, 20.0)));

        ScanReviewResponse review = service.createScan(teacherId, Role.TEACHER, request());

        assertThat(review.getProposedMarks().get(0).getMarksObtained()).isNull();
        assertThat(review.getWarnings())
                .anySatisfy(w -> assertThat(w).containsIgnoringCase("question 1").contains("blank"));
    }

    // ── Warnings rather than refusals ────────────────────────────────────────

    @Test
    @DisplayName("a mark above the question's maximum is flagged, and the scan still comes back for review")
    void flagsOverMaxWithoutRefusingTheScan() {
        extractor.sheet = sheet(List.of(
                new ExtractedQuestionMark("1", 18.0, 10.0),
                new ExtractedQuestionMark("2", 15.0, 20.0)));

        ScanReviewResponse review = service.createScan(teacherId, Role.TEACHER, request());

        assertThat(review.getStatus()).isEqualTo(ExamScan.STATUS_EXTRACTED);
        assertThat(review.getWarnings())
                .anySatisfy(w -> assertThat(w).containsIgnoringCase("question 1").contains("out of 10"));
    }

    @Test
    @DisplayName("a written total that disagrees with the marks read is flagged")
    void flagsTotalMismatch() {
        ExtractedMarksSheet base = sheet(List.of(
                new ExtractedQuestionMark("1", 8.0, 10.0),
                new ExtractedQuestionMark("2", 15.0, 20.0)));
        extractor.sheet = new ExtractedMarksSheet(base.rollNumber(), base.studentName(),
                base.courseCode(), base.examDate(), base.questionMarks(),
                25.0, "HIGH", List.of());   // page says 25, marks sum to 23

        ScanReviewResponse review = service.createScan(teacherId, Role.TEACHER, request());

        assertThat(review.getWarnings())
                .anySatisfy(w -> assertThat(w).contains("does not match"));
    }

    @Test
    @DisplayName("a roll number that is not on the roster leaves the student unmatched")
    void unknownRollNumberIsNotMatched() {
        when(dataRepo.findEnrolledStudentByRollNumber(any(), any())).thenReturn(Optional.empty());
        extractor.sheet = sheet(List.of(new ExtractedQuestionMark("1", 8.0, 10.0)));

        ScanReviewResponse review = service.createScan(teacherId, Role.TEACHER, request());

        assertThat(review.getMatchedStudentId()).isNull();
        assertThat(review.getWarnings())
                .anySatisfy(w -> assertThat(w).contains("not on this course's roster"));
    }

    @Test
    @DisplayName("a name that does not match the roll number's owner is flagged but does not override the match")
    void flagsNameMismatchButKeepsRollNumberMatch() {
        ExtractedMarksSheet base = sheet(List.of(new ExtractedQuestionMark("1", 8.0, 10.0)));
        extractor.sheet = new ExtractedMarksSheet(base.rollNumber(), "Bilal Ahmed",
                base.courseCode(), base.examDate(), base.questionMarks(), null, "HIGH", List.of());

        ScanReviewResponse review = service.createScan(teacherId, Role.TEACHER, request());

        assertThat(review.getMatchedStudentId()).isEqualTo(studentId);
        assertThat(review.getWarnings())
                .anySatisfy(w -> assertThat(w).contains("Bilal Ahmed").contains("Ayesha Siddiqui"));
    }

    @Test
    @DisplayName("a course code that differs from the exam's is flagged")
    void flagsCourseCodeMismatch() {
        ExtractedMarksSheet base = sheet(List.of(new ExtractedQuestionMark("1", 8.0, 10.0)));
        extractor.sheet = new ExtractedMarksSheet(base.rollNumber(), base.studentName(),
                "CS-401", base.examDate(), base.questionMarks(), null, "HIGH", List.of());

        ScanReviewResponse review = service.createScan(teacherId, Role.TEACHER, request());

        assertThat(review.getWarnings())
                .anySatisfy(w -> assertThat(w).contains("CS-401").contains("CS-363"));
    }

    @Test
    @DisplayName("a failed reading is still recorded, so the captured page is not lost")
    void failedReadingIsRecorded() {
        extractor.failure = new MarksSheetExtractor.MarksExtractionException("page too blurred");

        ScanReviewResponse review = service.createScan(teacherId, Role.TEACHER, request());

        assertThat(review.getStatus()).isEqualTo(ExamScan.STATUS_FAILED);
        assertThat(review.getErrorMessage()).contains("page too blurred");
        verify(scanRepo).save(any());
    }

    // ── Reading never writes marks ───────────────────────────────────────────

    @Test
    @DisplayName("reading a page records no marks — only confirmation does")
    void readingAloneWritesNoMarks() {
        extractor.sheet = sheet(List.of(
                new ExtractedQuestionMark("1", 8.0, 10.0),
                new ExtractedQuestionMark("2", 15.0, 20.0)));

        service.createScan(teacherId, Role.TEACHER, request());

        verify(examService, never()).writeResult(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("confirmation records the teacher's marks, not the extractor's")
    void confirmationRecordsTheTeachersMarks() {
        ExamScan scan = new ExamScan();
        scan.setExamId(examId);
        scan.setUploadedBy(teacherId);
        scan.setImageKey("exam-scans/x/page.jpg");
        scan.setStatus(ExamScan.STATUS_EXTRACTED);
        when(scanRepo.findById(any())).thenReturn(Optional.of(scan));

        ConfirmScanRequest req = new ConfirmScanRequest();
        req.setStudentId(studentId);
        // The teacher corrected question 1 from the 8 that was read to 9.
        req.setMarks(List.of(entry(q1Id, "9"), entry(q2Id, "15")));

        service.confirmScan(UUID.randomUUID(), teacherId, Role.TEACHER, req);

        verify(examService).writeResult(any(), eq(studentId), eq(req.getMarks()),
                eq(teacherId), eq(ExamResult.SOURCE_SCAN), any(), any());
        assertThat(scan.getStatus()).isEqualTo(ExamScan.STATUS_CONFIRMED);
        assertThat(scan.getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("an existing result for the student is flagged before it is replaced")
    void warnsWhenMarksAlreadyExist() {
        when(resultRepo.findByExamIdAndStudentId(examId, studentId))
                .thenReturn(Optional.of(new ExamResult()));
        extractor.sheet = sheet(List.of(new ExtractedQuestionMark("1", 8.0, 10.0)));

        ScanReviewResponse review = service.createScan(teacherId, Role.TEACHER, request());

        assertThat(review.getWarnings())
                .anySatisfy(w -> assertThat(w).contains("already recorded"));
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private CreateScanRequest request() {
        CreateScanRequest req = new CreateScanRequest();
        req.setImageKey("exam-scans/abc/page.jpg");
        req.setImageName("page.jpg");
        req.setExamId(examId);
        return req;
    }

    private ExtractedMarksSheet sheet(List<ExtractedQuestionMark> marks) {
        return new ExtractedMarksSheet("B21-CS-045", "Ayesha Siddiqui", "CS-363",
                "2026-03-12", marks, null, "HIGH", List.of());
    }

    private Exam openExam() {
        Exam exam = new Exam();
        setId(exam, examId);
        exam.setPscId(pscId);
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

    /** Stands in for the Claude reader, so tests never make a network call. */
    private static class StubExtractor implements MarksSheetExtractor {
        ExtractedMarksSheet sheet;
        MarksExtractionException failure;

        @Override
        public ExtractedMarksSheet extract(byte[] image, String contentType,
                                           List<QuestionExpectation> expectations) {
            if (failure != null) throw failure;
            return sheet;
        }

        @Override
        public String identifier() {
            return "stub";
        }
    }

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
