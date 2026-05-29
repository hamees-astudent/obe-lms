package com.lms.infrastructure.security.jwt;

import com.lms.infrastructure.security.UserPrincipal;
import com.lms.shared.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Creates and validates HS256-signed JWT tokens using jjwt 0.12.x.
 *
 * Token claims:
 *   sub   — user email
 *   userId — UUID string
 *   role  — Role enum name (ADMIN / TEACHER / ASSISTANT / STUDENT)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    // ── Token generation ─────────────────────────────────────────────────────

    public String generateAccessToken(UserPrincipal principal) {
        return buildToken(principal, jwtProperties.getExpirationMs());
    }

    public String generateRefreshToken(UserPrincipal principal) {
        return buildToken(principal, jwtProperties.getRefreshExpirationMs());
    }

    private String buildToken(UserPrincipal principal, long expirationMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(principal.getEmail())
                .claim("userId", principal.getId().toString())
                .claim("role", principal.getRole().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(secretKey())
                .compact();
    }

    // ── Token validation ─────────────────────────────────────────────────────

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.debug("JWT token is empty or null: {}", e.getMessage());
        }
        return false;
    }

    // ── Claims extraction ────────────────────────────────────────────────────

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public Role extractRole(String token) {
        return Role.valueOf(parseClaims(token).get("role", String.class));
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
