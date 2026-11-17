package com.lms.infrastructure.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppUrlsTest {

    @Test
    @DisplayName("links join the base and the path whatever the slashes, and keep a path prefix")
    void buildsLinks() {
        AppUrls urls = new AppUrls(" https://lms.example.edu/ ");
        assertThat(urls.baseUrl()).isEqualTo("https://lms.example.edu");
        assertThat(urls.url("/courses/42")).isEqualTo("https://lms.example.edu/courses/42");
        assertThat(urls.url("courses/42")).isEqualTo("https://lms.example.edu/courses/42");
        assertThat(urls.url("")).isEqualTo("https://lms.example.edu");

        AppUrls prefixed = new AppUrls("https://example.edu:8443/lms/");
        assertThat(prefixed.url("/reset-password")).isEqualTo("https://example.edu:8443/lms/reset-password");
        assertThat(prefixed.origin()).isEqualTo("https://example.edu:8443");
        assertThat(prefixed.isLocal()).isFalse();
    }

    @Test
    @DisplayName("query values are percent-encoded")
    void encodesQueryValues() {
        AppUrls urls = new AppUrls("https://lms.example.edu");
        assertThat(urls.link("/reset-password").queryParam("token", "a b&c=d").build().encode().toUriString())
                .isEqualTo("https://lms.example.edu/reset-password?token=a%20b%26c%3Dd");
    }

    @Test
    @DisplayName("localhost is recognised, so a misconfigured deployment can be warned about")
    void detectsLocalhost() {
        assertThat(new AppUrls("http://localhost:8080").isLocal()).isTrue();
        assertThat(new AppUrls("http://127.0.0.1:5173").isLocal()).isTrue();
    }

    @Test
    @DisplayName("values that cannot produce working links stop startup with a clear message")
    void rejectsUnusableValues() {
        for (String bad : new String[] {"", "lms.example.edu", "ftp://lms.example.edu", "https://lms.example.edu?x=1",
                "https://lms.example.edu/#/home", "https://user:pw@lms.example.edu", "http://"}) {
            assertThatThrownBy(() -> new AppUrls(bad)).as(bad)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_BASE_URL");
        }
    }
}
