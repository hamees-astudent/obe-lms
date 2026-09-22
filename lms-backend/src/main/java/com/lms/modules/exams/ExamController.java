package com.lms.modules.exams;

import com.lms.infrastructure.security.UserPrincipal;
import com.lms.modules.exams.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Exam definitions, scanned copies, and recorded marks.
 *
 * <p>The role checks here are the coarse gate; which <em>offering</em> a
 * teacher may touch is enforced in the service, since {@code hasRole('TEACHER')}
 * alone would let any teacher record marks on any course.
 */
@RestController
@RequiredArgsConstructor
public class ExamController {

    private final ExamService     examService;
    private final ExamScanService scanService;

    // ── Exams ────────────────────────────────────────────────────────────────

    @PostMapping("/api/offerings/{pscId}/exams")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public ExamResponse createExam(
            @PathVariable UUID pscId,
            @RequestBody @Valid CreateExamRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        req.setPscId(pscId);
        return examService.createExam(principal.getId(), principal.getRole(), req);
    }

    @GetMapping("/api/offerings/{pscId}/exams")
    @PreAuthorize("isAuthenticated()")
    public List<ExamResponse> listExams(@PathVariable UUID pscId) {
        return examService.listExams(pscId);
    }

    @GetMapping("/api/exams/{examId}")
    @PreAuthorize("isAuthenticated()")
    public ExamResponse getExam(@PathVariable UUID examId) {
        return examService.getExam(examId);
    }

    @PutMapping("/api/exams/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public ExamResponse updateExam(
            @PathVariable UUID examId,
            @RequestBody @Valid UpdateExamRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return examService.updateExam(examId, principal.getId(), principal.getRole(), req);
    }

    @PatchMapping("/api/exams/{examId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ExamResponse changeStatus(
            @PathVariable UUID examId,
            @RequestBody @Valid ChangeExamStatusRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return examService.changeStatus(examId, principal.getId(), principal.getRole(), req.getStatus());
    }

    @DeleteMapping("/api/exams/{examId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public void deleteExam(@PathVariable UUID examId,
                           @AuthenticationPrincipal UserPrincipal principal) {
        examService.deleteExam(examId, principal.getId(), principal.getRole());
    }

    /** CLOs available to map this exam's questions to. */
    @GetMapping("/api/exams/{examId}/clos")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public List<CloMappingResponse> listAvailableClos(@PathVariable UUID examId) {
        return examService.listAvailableClos(examId);
    }

    /** The offering's roster, with who already has marks. */
    @GetMapping("/api/exams/{examId}/roster")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public List<RosterEntryResponse> listRoster(@PathVariable UUID examId,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return examService.listRoster(examId, principal.getId(), principal.getRole());
    }

    // ── Scanning ─────────────────────────────────────────────────────────────

    /**
     * Read a captured exam page.
     *
     * <p>The image is uploaded to {@code POST /api/files} first and passed here
     * by object key. This returns a <em>proposal</em> for review — no marks are
     * recorded until {@code /confirm}.
     */
    @PostMapping("/api/exam-scans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public ScanReviewResponse createScan(
            @RequestBody @Valid CreateScanRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return scanService.createScan(principal.getId(), principal.getRole(), req);
    }

    @GetMapping("/api/exam-scans/{scanId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public ScanReviewResponse getScan(@PathVariable UUID scanId,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return scanService.getScan(scanId, principal.getId(), principal.getRole());
    }

    /** The review queue for an exam. */
    @GetMapping("/api/exams/{examId}/scans")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public List<ScanSummaryResponse> listScans(@PathVariable UUID examId,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return scanService.listScans(examId, principal.getId(), principal.getRole());
    }

    /** Attach a scan whose exam could not be resolved automatically. */
    @PatchMapping("/api/exam-scans/{scanId}/exam/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public ScanReviewResponse assignExam(@PathVariable UUID scanId,
                                         @PathVariable UUID examId,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return scanService.assignExam(scanId, examId, principal.getId(), principal.getRole());
    }

    /**
     * Record the marks the teacher confirmed for a scan.
     * This is the only path from a scanned page to a student's record.
     */
    @PostMapping("/api/exam-scans/{scanId}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public ExamResultResponse confirmScan(
            @PathVariable UUID scanId,
            @RequestBody @Valid ConfirmScanRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return scanService.confirmScan(scanId, principal.getId(), principal.getRole(), req);
    }

    @DeleteMapping("/api/exam-scans/{scanId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public void discardScan(@PathVariable UUID scanId,
                            @AuthenticationPrincipal UserPrincipal principal) {
        scanService.discardScan(scanId, principal.getId(), principal.getRole());
    }

    // ── Results ──────────────────────────────────────────────────────────────

    /** Manual mark entry, for a copy that was not scanned. */
    @PostMapping("/api/exams/{examId}/results")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public ExamResultResponse recordMarks(
            @PathVariable UUID examId,
            @RequestBody @Valid RecordMarksRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return examService.recordMarks(examId, principal.getId(), principal.getRole(), req);
    }

    @GetMapping("/api/exams/{examId}/results")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public List<ExamResultResponse> listResults(@PathVariable UUID examId,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return examService.listResults(examId, principal.getId(), principal.getRole());
    }

    /** A student's own exam result. Students may read only their own. */
    @GetMapping("/api/exams/{examId}/results/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT') or #studentId == principal.id")
    public ExamResultResponse getStudentResult(@PathVariable UUID examId,
                                               @PathVariable UUID studentId) {
        return examService.getStudentResult(examId, studentId);
    }

    @DeleteMapping("/api/exams/{examId}/results/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public void deleteResult(@PathVariable UUID examId,
                             @PathVariable UUID studentId,
                             @AuthenticationPrincipal UserPrincipal principal) {
        examService.deleteResult(examId, studentId, principal.getId(), principal.getRole());
    }
}
