package com.lms.infrastructure.security.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code jwt.*} from application.yml.
 * Registered via {@code @EnableConfigurationProperties(JwtProperties.class)} in SecurityConfig.
 */
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {

    /** Raw secret string — must be at least 32 characters (256 bits) for HS256. */
    private String secret;

    /** Access token lifetime in milliseconds (default 24 h). */
    private long expirationMs;

    /** Refresh token lifetime in milliseconds (default 7 d). */
    private long refreshExpirationMs;
}
