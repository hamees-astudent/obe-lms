package com.lms.infrastructure.web;

import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * The single place the backend learns its own public address and builds
 * absolute links from it — for anything that leaves the request/response
 * cycle and must work on someone else's machine: links in e-mails,
 * notifications and exported documents, redirect targets, CORS origins.
 *
 * <p>Within a request the browser already knows the host, so API responses
 * and the frontend keep using relative paths ({@code /api/...}); use this class
 * only when there is no request to be relative to.
 *
 * <pre>{@code
 * appUrls.url("/courses/" + id)                        // https://lms.example.edu/courses/42
 * appUrls.link("/reset-password").queryParam("token", t)
 *        .build().encode().toUriString()               // query values encoded
 * }</pre>
 *
 * <p>The configured value is checked at startup: it must be an absolute
 * {@code http(s)} URL without a query or fragment. A trailing slash is
 * dropped, and a path prefix ({@code https://example.edu/lms}) is kept in
 * every link.
 */
public final class AppUrls {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]");

    private final URI base;

    public AppUrls(String baseUrl) {
        this.base = parse(baseUrl);
    }

    /** The public base URL without a trailing slash, e.g. {@code https://example.edu/lms}. */
    public String baseUrl() {
        return base.toString();
    }

    /** Scheme, host and port only, e.g. {@code https://example.edu} — the browser origin, for CORS. */
    public String origin() {
        return base.getScheme() + "://" + base.getRawAuthority();
    }

    /**
     * An absolute URL for an app path, e.g. {@code url("/courses/42")}. The
     * path is appended as written (a leading slash is optional), so encode any
     * user-supplied segment yourself, or use {@link #link(String)}.
     */
    public String url(String path) {
        return baseUrl() + normalisePath(path);
    }

    /**
     * A builder for an absolute URL to an app path, for adding query
     * parameters. Finish with {@code .build().encode().toUriString()} so values
     * are percent-encoded.
     */
    public UriComponentsBuilder link(String path) {
        return UriComponentsBuilder.fromUriString(url(path));
    }

    /** Whether the base URL points at this machine — links built from it work nowhere else. */
    public boolean isLocal() {
        return LOCAL_HOSTS.contains(base.getHost().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return baseUrl();
    }

    private static String normalisePath(String path) {
        if (path == null || path.isBlank() || path.equals("/")) return "";
        return path.startsWith("/") ? path : "/" + path;
    }

    static URI parse(String value) {
        String v = value == null ? "" : value.strip();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        if (v.isEmpty()) throw invalid(value, "it is empty");
        URI uri;
        try {
            uri = new URI(v);
        } catch (URISyntaxException e) {
            throw invalid(value, e.getReason());
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw invalid(value, "it must start with http:// or https://");
        if (uri.getHost() == null || uri.getHost().isBlank()) throw invalid(value, "it has no host");
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw invalid(value, "it must not contain a query (?...) or fragment (#...)");
        }
        if (uri.getRawUserInfo() != null) throw invalid(value, "it must not contain a user name or password");
        return uri;
    }

    private static IllegalStateException invalid(String value, String why) {
        return new IllegalStateException("app.base-url (APP_BASE_URL) \"" + value + "\" is not usable: " + why
                + ". Set it to the address users open the app at, e.g. https://lms.example.edu");
    }
}
