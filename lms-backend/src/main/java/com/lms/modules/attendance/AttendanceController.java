package com.lms.modules.attendance;

import com.lms.infrastructure.security.UserPrincipal;
import com.lms.modules.attendance.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    // ═══════════════════════════════════════════════════════════════════════
    // Sessions
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/api/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TEACHER','ASSISTANT','ADMIN')")
    public SessionResponse createSession(
            @Valid @RequestBody CreateSessionRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return attendanceService.createSession(principal.getId(), req);
    }

    @PostMapping("/api/sessions/{sessionId}/close")
    @PreAuthorize("hasAnyRole('TEACHER','ASSISTANT','ADMIN')")
    public SessionResponse closeSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return attendanceService.closeSession(sessionId, principal.getId());
    }

    @GetMapping("/api/offerings/{pscId}/sessions")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public List<SessionResponse> listSessions(
            @PathVariable UUID pscId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attendanceService.listSessions(pscId, date);
    }

    @GetMapping("/api/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT','STUDENT')")
    public SessionResponse getSession(@PathVariable UUID sessionId) {
        return attendanceService.getSession(sessionId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Records — mark / bulk-mark attendance
    // ═══════════════════════════════════════════════════════════════════════

    @PutMapping("/api/sessions/{sessionId}/records/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ASSISTANT','ADMIN')")
    public AttendanceRecordResponse markRecord(
            @PathVariable UUID sessionId,
            @PathVariable UUID studentId,
            @Valid @RequestBody MarkAttendanceRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return attendanceService.markRecord(sessionId, studentId, req, principal.getId());
    }

    @PostMapping("/api/sessions/{sessionId}/records/bulk")
    @PreAuthorize("hasAnyRole('TEACHER','ASSISTANT','ADMIN')")
    public List<AttendanceRecordResponse> bulkMark(
            @PathVariable UUID sessionId,
            @Valid @RequestBody BulkMarkRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return attendanceService.bulkMark(sessionId, req, principal.getId());
    }

    @GetMapping("/api/sessions/{sessionId}/records")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public List<AttendanceRecordResponse> getSessionRecords(@PathVariable UUID sessionId) {
        return attendanceService.getRecordsForSession(sessionId);
    }

    @GetMapping("/api/admin/students/{studentId}/attendance")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public List<AttendanceRecordResponse> getStudentRecords(@PathVariable UUID studentId) {
        return attendanceService.getRecordsForStudent(studentId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Attendance summary
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/offerings/{pscId}/attendance/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public AttendanceSummaryResponse getStudentSummary(
            @PathVariable UUID pscId,
            @PathVariable UUID studentId) {
        return attendanceService.getAttendanceSummary(pscId, studentId);
    }

    @GetMapping("/api/me/attendance/{pscId}")
    @PreAuthorize("hasRole('STUDENT')")
    public AttendanceSummaryResponse myAttendanceSummary(
            @PathVariable UUID pscId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return attendanceService.getAttendanceSummary(pscId, principal.getId());
    }
}
