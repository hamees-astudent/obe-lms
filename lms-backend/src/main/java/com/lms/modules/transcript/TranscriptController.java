package com.lms.modules.transcript;

import com.lms.modules.transcript.dto.TranscriptResponse;
import com.lms.modules.transcript.dto.TranscriptSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TranscriptController {

    private final TranscriptService transcriptService;

    // ── Admin / Teacher endpoints ────────────────────────────────────────────

    /**
     * List all transcripts generated for a semester.
     * Roles: ADMIN
     */
    @GetMapping("/api/admin/transcripts/semester/{semesterId}")
    public List<TranscriptSummaryResponse> getSemesterTranscripts(
            @PathVariable UUID semesterId) {
        return transcriptService.getSemesterTranscripts(semesterId);
    }

    /**
     * List transcript history for a specific student.
     * Roles: ADMIN, TEACHER
     */
    @GetMapping("/api/admin/students/{studentId}/transcripts")
    public List<TranscriptSummaryResponse> getStudentTranscripts(
            @PathVariable UUID studentId) {
        return transcriptService.getStudentTranscripts(studentId);
    }

    /**
     * Manually trigger transcript re-generation for all students in a semester.
     * Useful for corrections after grading updates.
     * Roles: ADMIN
     */
    @PostMapping("/api/admin/transcripts/semester/{semesterId}/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void triggerRegeneration(
            @PathVariable UUID semesterId,
            @RequestParam UUID programId,
            Authentication auth) {
        transcriptService.regenerateTranscriptsForSemester(semesterId, programId, auth.getName());
    }

    // ── Student / authenticated endpoints ────────────────────────────────────

    /**
     * Get the current student's transcript list.
     * Roles: STUDENT
     */
    @GetMapping("/api/me/transcripts")
    public List<TranscriptSummaryResponse> getMyTranscripts(Authentication auth) {
        UUID studentId = transcriptService.resolveStudentId(auth.getName());
        return transcriptService.getStudentTranscripts(studentId);
    }

    /**
     * Get full transcript detail (including snapshot).
     * ADMIN/TEACHER: any transcript; STUDENT: own only.
     */
    @GetMapping("/api/transcripts/{id}")
    public TranscriptResponse getTranscript(
            @PathVariable UUID id,
            Authentication auth) {
        return transcriptService.getTranscript(id, auth);
    }

    /**
     * Download transcript as PDF.
     * ADMIN/TEACHER: any transcript; STUDENT: own only.
     */
    @GetMapping("/api/transcripts/{id}/pdf")
    public ResponseEntity<byte[]> getTranscriptPdf(
            @PathVariable UUID id,
            Authentication auth) {
        byte[] pdf = transcriptService.getTranscriptPdf(id, auth);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"transcript-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
