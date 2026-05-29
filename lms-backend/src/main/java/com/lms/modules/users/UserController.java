package com.lms.modules.users;

import com.lms.infrastructure.security.UserPrincipal;
import com.lms.modules.users.dto.*;
import com.lms.shared.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * User management REST endpoints.
 *
 * <pre>
 * Admin (ROLE_ADMIN, secured by SecurityConfig /api/admin/**):
 *   GET    /api/admin/users                       — paginated list (filters: role, status)
 *   POST   /api/admin/users                       — create user
 *   GET    /api/admin/users/{id}                  — get user detail
 *   PUT    /api/admin/users/{id}                  — update name / email
 *   PATCH  /api/admin/users/{id}/role             — change role
 *   PATCH  /api/admin/users/{id}/status           — change status
 *   GET    /api/admin/users/{id}/student-profile  — get student profile
 *   PUT    /api/admin/users/{id}/student-profile  — upsert student profile
 *   GET    /api/admin/users/{id}/teacher-profile  — get teacher profile
 *   PUT    /api/admin/users/{id}/teacher-profile  — upsert teacher profile
 *
 * Self-service (any authenticated user):
 *   GET    /api/users/me                    — get own detail
 *   PATCH  /api/users/me/name               — update own name
 *   PUT    /api/users/me/password           — change own password
 *   GET    /api/users/me/student-profile    — get own student profile
 *   PUT    /api/users/me/student-profile    — upsert own student profile
 *   GET    /api/users/me/teacher-profile    — get own teacher profile
 *   PUT    /api/users/me/teacher-profile    — upsert own teacher profile
 * </pre>
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ═══════════════════════════════════════════════════════════════════════════
    // Admin — /api/admin/users
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/api/admin/users")
    public Page<UserSummaryResponse> listUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return userService.listUsers(role, status, pageable);
    }

    @PostMapping("/api/admin/users")
    public ResponseEntity<UserSummaryResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUser(request));
    }

    @GetMapping("/api/admin/users/{id}")
    public UserDetailResponse getUserDetail(@PathVariable UUID id) {
        return userService.getUserDetail(id);
    }

    @PutMapping("/api/admin/users/{id}")
    public UserDetailResponse updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateUser(id, request);
    }

    @PatchMapping("/api/admin/users/{id}/role")
    public UserSummaryResponse changeRole(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeRoleRequest request) {
        return userService.changeRole(id, request);
    }

    @PatchMapping("/api/admin/users/{id}/status")
    public UserSummaryResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeStatusRequest request) {
        return userService.changeStatus(id, request);
    }

    // ── Admin: profile management ─────────────────────────────────────────────

    @GetMapping("/api/admin/users/{id}/student-profile")
    public StudentProfileResponse getAdminStudentProfile(@PathVariable UUID id) {
        return userService.getStudentProfile(id);
    }

    @PutMapping("/api/admin/users/{id}/student-profile")
    public StudentProfileResponse upsertAdminStudentProfile(
            @PathVariable UUID id,
            @Valid @RequestBody StudentProfileRequest request) {
        return userService.upsertStudentProfile(id, request);
    }

    @GetMapping("/api/admin/users/{id}/teacher-profile")
    public TeacherProfileResponse getAdminTeacherProfile(@PathVariable UUID id) {
        return userService.getTeacherProfile(id);
    }

    @PutMapping("/api/admin/users/{id}/teacher-profile")
    public TeacherProfileResponse upsertAdminTeacherProfile(
            @PathVariable UUID id,
            @Valid @RequestBody TeacherProfileRequest request) {
        return userService.upsertTeacherProfile(id, request);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Self-service — /api/users/me
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/api/users/me")
    public UserDetailResponse getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getMe(principal.getId());
    }

    @PatchMapping("/api/users/me/name")
    public UserDetailResponse updateMyName(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody @Valid NameUpdateRequest request) {
        return userService.updateMyName(principal.getId(), request.name());
    }

    @PutMapping("/api/users/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeOwnPassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changeOwnPassword(principal.getId(), request);
    }

    // ── Self-service: student profile ─────────────────────────────────────────

    @GetMapping("/api/users/me/student-profile")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentProfileResponse getMyStudentProfile(
            @AuthenticationPrincipal UserPrincipal principal) {
        return userService.getStudentProfile(principal.getId());
    }

    @PutMapping("/api/users/me/student-profile")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentProfileResponse upsertMyStudentProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StudentProfileRequest request) {
        return userService.upsertStudentProfile(principal.getId(), request);
    }

    // ── Self-service: teacher/assistant profile ───────────────────────────────

    @GetMapping("/api/users/me/teacher-profile")
    @PreAuthorize("hasAnyRole('TEACHER','ASSISTANT')")
    public TeacherProfileResponse getMyTeacherProfile(
            @AuthenticationPrincipal UserPrincipal principal) {
        return userService.getTeacherProfile(principal.getId());
    }

    @PutMapping("/api/users/me/teacher-profile")
    @PreAuthorize("hasAnyRole('TEACHER','ASSISTANT')")
    public TeacherProfileResponse upsertMyTeacherProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TeacherProfileRequest request) {
        return userService.upsertTeacherProfile(principal.getId(), request);
    }

    // ── Inline DTO for name-only update ──────────────────────────────────────

    record NameUpdateRequest(@NotBlank @Size(max = 120) String name) {}
}
