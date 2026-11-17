package com.lms.infrastructure.web;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where this application is reached from outside ({@code app.base-url},
 * environment variable {@code APP_BASE_URL}).
 *
 * <p>Read it through {@link AppUrls}, never directly: that is where the value is
 * checked and normalised, and where links are built from it.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppUrlProperties {

    /**
     * The public address of the app as users open it in a browser — scheme,
     * host, optional port and optional path prefix, e.g.
     * {@code https://lms.example.edu} or {@code https://example.edu/lms}.
     * Behind a reverse proxy this is the proxy's address, not the server's.
     */
    private String baseUrl = "http://localhost:8080";
}
