package com.lms.infrastructure.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the bundled React SPA for every client-side route.
 *
 * <p>The router lives in the browser, so a reload or deep link to e.g.
 * {@code /courses/123} reaches the server as a request for a file that does
 * not exist. Without this fallback that was a "not found" error instead of
 * the app. Real static files are served as-is; any other path gets
 * {@code index.html} and the SPA routes it.
 *
 * <p>API and actuator paths are never rewritten: an unknown endpoint must stay
 * a 404 ProblemDetail, not an HTML page a client would try to parse as JSON.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private static final String STATIC_ROOT = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_ROOT)
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    /** Resolves a real file when one exists, otherwise {@code index.html}. */
    static final class SpaFallbackResolver extends PathResourceResolver {

        private static final Resource INDEX = new ClassPathResource("static/index.html");

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            return isSpaRoute(resourcePath) && INDEX.exists() ? INDEX : null;
        }

        /**
         * A path the SPA router owns. Excludes the backend's own namespaces,
         * and anything with a file extension so a missing asset stays a 404
         * rather than HTML served as JavaScript.
         */
        static boolean isSpaRoute(String path) {
            if (path.startsWith("api/") || path.equals("api")
                    || path.startsWith("actuator/") || path.equals("actuator")) {
                return false;
            }
            String lastSegment = path.substring(path.lastIndexOf('/') + 1);
            return !lastSegment.contains(".");
        }
    }
}
