package com.lms.infrastructure.security;

import com.lms.infrastructure.security.jwt.JwtAuthenticationFilter;
import com.lms.infrastructure.security.jwt.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration.
 *
 * <ul>
 *   <li>Stateless JWT authentication — no HTTP sessions.</li>
 *   <li>Role-based access: ADMIN, TEACHER, ASSISTANT, STUDENT.</li>
 *   <li>Method-level security enabled via {@code @PreAuthorize}.</li>
 *   <li>CORS: permissive for /api/** (restrict {@code allowed-origins} in production).</li>
 * </ul>
 *
 * Public endpoints (no token required):
 * <ul>
 *   <li>POST  /api/auth/login</li>
 *   <li>POST  /api/auth/refresh</li>
 *   <li>GET   /actuator/health/**</li>
 *   <li>GET   / , /index.html, /assets/**, /favicon.ico  (SPA static resources)</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // ── Security filter chain ────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // ── Auth endpoints (public) ──────────────────────────
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/password-reset/request",
                                "/api/auth/password-reset/confirm").permitAll()
                        // ── Actuator health probe ────────────────────────────
                        .requestMatchers("/actuator/health/**").permitAll()
                        // ── SPA static resources ─────────────────────────────
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/favicon.ico",
                                "/vite.svg").permitAll()
                        // ── Error page ───────────────────────────────────────
                        .requestMatchers("/error").permitAll()

                        // ── Role-gated admin-only endpoints ──────────────────
                        .requestMatchers("/api/admin/**")
                                .hasRole("ADMIN")

                        // ── Everything else requires a valid token ───────────
                        .anyRequest().authenticated()
                )

                // Inject JWT filter before the default username/password filter
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)

                // Return proper HTTP status codes (not redirect to login page)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                                        "Unauthorized"))
                        .accessDeniedHandler((req, res, e) ->
                                res.sendError(HttpServletResponse.SC_FORBIDDEN,
                                        "Forbidden"))
                )
                .build();
    }

    // ── Shared beans ────────────────────────────────────────────────────────

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ── CORS ─────────────────────────────────────────────────────────────────

    /**
     * CORS configuration for /api/** routes.
     *
     * In production, replace the allowed-origin pattern with the actual domain.
     * The bundled SPA (same-origin) does not need CORS — this only matters when
     * the Vite dev server (localhost:5173) is used directly instead of the proxy.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // TODO: replace with specific origin(s) in production
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
