package com.lms.infrastructure.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.lms.infrastructure.web.SpaWebConfig.SpaFallbackResolver.isSpaRoute;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A reload on a client-side route must get the SPA, but an unknown API
 * endpoint or a missing asset must stay a 404 — HTML in place of JSON or
 * JavaScript fails in far more confusing ways than a plain "not found".
 */
class SpaWebConfigTest {

    @DisplayName("client-side routes fall back to index.html")
    @ParameterizedTest
    @ValueSource(strings = {
            "courses", "courses/3f2a1c9e-0000-4000-8000-000000000000",
            "assessment/abc/quizzes", "dashboard", "apiary", "users/api"})
    void spaRoutes(String path) {
        assertThat(isSpaRoute(path)).isTrue();
    }

    @DisplayName("API, actuator and file paths are never rewritten")
    @ParameterizedTest
    @ValueSource(strings = {
            "api", "api/courses", "api/unknown/endpoint",
            "actuator", "actuator/health",
            "assets/missing-chunk.js", "favicon.ico", "courses/report.pdf"})
    void notSpaRoutes(String path) {
        assertThat(isSpaRoute(path)).isFalse();
    }
}
