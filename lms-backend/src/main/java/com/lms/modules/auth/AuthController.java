package com.lms.modules.auth;

import com.lms.modules.auth.dto.LoginRequest;
import com.lms.modules.auth.dto.LoginResponse;
import com.lms.modules.auth.dto.PasswordResetConfirmDto;
import com.lms.modules.auth.dto.PasswordResetRequestDto;
import com.lms.modules.auth.dto.RefreshRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints — all routes under {@code /api/auth} are public
 * (no Bearer token required).
 *
 * <pre>
 * POST /api/auth/login                   — exchange credentials for token pair
 * POST /api/auth/refresh                 — rotate a refresh token for a new pair
 * POST /api/auth/logout                  — revoke a refresh token
 * POST /api/auth/password-reset/request  — send reset email (always 202)
 * POST /api/auth/password-reset/confirm  — apply new password via reset token
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController {

    private final AuthService authService;

    // ── Login ─────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
    }

    // ── Password reset ────────────────────────────────────────────────────────

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(
            @Valid @RequestBody PasswordResetRequestDto request) {
        authService.requestPasswordReset(request);
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmDto request) {
        authService.confirmPasswordReset(request);
    }
}
