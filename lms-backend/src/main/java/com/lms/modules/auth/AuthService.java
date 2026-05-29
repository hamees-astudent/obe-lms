package com.lms.modules.auth;

import com.lms.infrastructure.security.UserPrincipal;
import com.lms.infrastructure.security.jwt.JwtProperties;
import com.lms.infrastructure.security.jwt.JwtTokenProvider;
import com.lms.modules.auth.dto.LoginRequest;
import com.lms.modules.auth.dto.LoginResponse;
import com.lms.modules.auth.dto.PasswordResetConfirmDto;
import com.lms.modules.auth.dto.PasswordResetRequestDto;
import com.lms.modules.auth.dto.RefreshRequest;
import com.lms.modules.users.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

/**
 * Handles all authentication concerns: login, token refresh, logout, and
 * the two-step password-reset flow.
 *
 * <p>Refresh tokens are stored in Redis under {@code auth:refresh:<token>} with a TTL
 * equal to {@code jwt.refresh-expiration-ms}.  On logout or rotation the key is deleted,
 * making previously issued refresh tokens immediately unusable.</p>
 *
 * <p>Password-reset tokens are 128-bit random strings stored under
 * {@code auth:reset:<token>} with a 15-minute TTL.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
class AuthService {

    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String RESET_PREFIX   = "auth:reset:";

    private final AuthenticationManager authenticationManager;
    private final UserRepository        userRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtTokenProvider      jwtTokenProvider;
    private final JwtProperties         jwtProperties;
    private final StringRedisTemplate   redisTemplate;
    private final JavaMailSender        mailSender;

    @Value("${spring.mail.username:noreply@lms.local}")
    private String mailFrom;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ── Login ─────────────────────────────────────────────────────────────────

    LoginResponse login(LoginRequest request) {
        // AuthenticationManager delegates to JpaUserDetailsService and BCrypt;
        // throws BadCredentialsException / DisabledException / LockedException on failure.
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        return issueTokenPair((UserPrincipal) auth.getPrincipal());
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    LoginResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();

        if (!jwtTokenProvider.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid or expired refresh token");
        }

        String email = redisTemplate.opsForValue().get(REFRESH_PREFIX + token);
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Refresh token has been revoked");
        }

        // Rotate: invalidate the consumed token, issue a fresh pair
        redisTemplate.delete(REFRESH_PREFIX + token);

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "User not found"));

        return issueTokenPair(UserPrincipal.from(user));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    void logout(RefreshRequest request) {
        redisTemplate.delete(REFRESH_PREFIX + request.refreshToken());
    }

    // ── Password reset — step 1: request ─────────────────────────────────────

    void requestPasswordReset(PasswordResetRequestDto dto) {
        // Always returns 202 regardless of whether the email exists — prevents enumeration.
        userRepository.findByEmail(dto.email()).ifPresent(user -> {
            String resetToken = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                    RESET_PREFIX + resetToken,
                    user.getEmail(),
                    Duration.ofMinutes(15));
            sendResetEmail(user.getEmail(), user.getName(), resetToken);
        });
    }

    // ── Password reset — step 2: confirm ─────────────────────────────────────

    @Transactional
    void confirmPasswordReset(PasswordResetConfirmDto dto) {
        String email = redisTemplate.opsForValue().get(RESET_PREFIX + dto.token());
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid or expired password reset token");
        }

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid password reset token"));

        user.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);

        // Revoke the one-time token only after the DB commit succeeds
        redisTemplate.delete(RESET_PREFIX + dto.token());
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private LoginResponse issueTokenPair(UserPrincipal principal) {
        String accessToken  = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(principal);

        redisTemplate.opsForValue().set(
                REFRESH_PREFIX + refreshToken,
                principal.getEmail(),
                Duration.ofMillis(jwtProperties.getRefreshExpirationMs()));

        return new LoginResponse(
                accessToken,
                refreshToken,
                jwtProperties.getExpirationMs(),
                new LoginResponse.UserInfo(
                        principal.getId(),
                        principal.getName(),
                        principal.getEmail(),
                        principal.getRole()));
    }

    private void sendResetEmail(String to, String name, String token) {
        try {
            var message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject("LMS \u2014 Password Reset Request");
            message.setText(String.format(
                    "Hi %s,%n%n" +
                    "Click the link below to reset your password (valid for 15 minutes):%n%n" +
                    "%s/reset-password?token=%s%n%n" +
                    "If you did not request this, please ignore this email.%n%n" +
                    "\u2014 LMS Team",
                    name, baseUrl, token));
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Failed to send password reset email to {}: {}", to, e.getMessage());
        }
    }
}
