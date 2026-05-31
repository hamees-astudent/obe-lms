package com.lms.modules.enrollment;

import com.lms.infrastructure.security.UserPrincipal;
import com.lms.modules.enrollment.dto.AdminEnrollRequest;
import com.lms.modules.enrollment.dto.EnrollmentResponse;
import com.lms.modules.enrollment.dto.SelfEnrollRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    // ═══════════════════════════════════════════════════════════════════════
    // Admin: enroll any student
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/api/admin/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public EnrollmentResponse adminEnroll(@Valid @RequestBody AdminEnrollRequest req) {
        return enrollmentService.enroll(req.pscId(), req.studentId(), req.courseRole());
    }

    @DeleteMapping("/api/admin/enrollments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EnrollmentResponse adminDrop(@PathVariable UUID id) {
        return enrollmentService.drop(id, null, true);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Student: self-enroll and self-drop
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/api/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    public EnrollmentResponse selfEnroll(
            @Valid @RequestBody SelfEnrollRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return enrollmentService.enroll(req.pscId(), principal.getId(), "STUDENT");
    }

    @DeleteMapping("/api/enrollments/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public EnrollmentResponse dropEnrollment(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return enrollmentService.drop(id, principal.getId(), isAdmin);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Student: view own enrollments
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/me/enrollments")
    @PreAuthorize("hasRole('STUDENT')")
    public List<EnrollmentResponse> myEnrollments(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (status != null) {
            return enrollmentService.listByStudentAndStatus(principal.getId(), status);
        }
        return enrollmentService.listByStudent(principal.getId());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Admin / Teacher: view rosters
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/admin/students/{studentId}/enrollments")
    @PreAuthorize("hasRole('ADMIN')")
    public List<EnrollmentResponse> studentEnrollments(
            @PathVariable UUID studentId,
            @RequestParam(required = false) String status) {
        if (status != null) {
            return enrollmentService.listByStudentAndStatus(studentId, status);
        }
        return enrollmentService.listByStudent(studentId);
    }

    @GetMapping("/api/offerings/{pscId}/enrollments")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public List<EnrollmentResponse> offeringRoster(
            @PathVariable UUID pscId,
            @RequestParam(required = false) String status) {
        return enrollmentService.listByPsc(pscId, status);
    }

    @GetMapping("/api/enrollments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public EnrollmentResponse getEnrollment(@PathVariable UUID id) {
        return enrollmentService.getEnrollment(id);
    }
}
