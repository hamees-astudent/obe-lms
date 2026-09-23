package com.lms.infrastructure.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns exceptions into RFC 7807 problem details the client can actually act on.
 *
 * <p>Spring's stock handling answers a failed {@code @Valid} with
 * {@code detail: "Invalid request content."} and drops every field error, so a
 * form rejected for one bad field looks identical to a form rejected for ten —
 * which is how validation failures end up surfacing as "something went wrong".
 * Each handler below keeps the standard problem shape and adds an
 * {@code errors} map of {@code field → message} where one exists.
 *
 * <p>Only exceptions the parent does <em>not</em> already map are declared here.
 * Re-declaring one it handles — {@code MaxUploadSizeExceededException}, for
 * instance, which it already answers with a 413 — makes the mapping ambiguous
 * and fails application startup.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Property carrying per-field messages; read by the client's error parser. */
    public static final String ERRORS_PROPERTY = "errors";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(),
                    fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "is invalid");
        }
        ex.getBindingResult().getGlobalErrors().forEach(ge ->
                fieldErrors.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));

        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        body.setTitle("Validation failed");
        body.setDetail(summarise(fieldErrors));
        body.setProperty(ERRORS_PROPERTY, fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Every exception the parent maps ends up here, and its stock {@code detail}
     * is framework text — "Content-Type 'application/json' is not supported",
     * "Required request body is missing" — that the client shows verbatim. Those
     * are replaced with a plain message for the status, and the original is
     * logged for whoever debugs it.
     *
     * <p>A {@link ResponseStatusException} carries a reason the service wrote
     * for the user ("Quiz already submitted"), so it is kept — except on a 500,
     * where the reason may embed an internal cause.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        // ErrorResponse exceptions (415, ResponseStatusException, …) arrive with
        // no body; the parent would build it after this point, out of reach.
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            body = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
        }
        if (body instanceof ProblemDetail problem) {
            boolean serviceMessage = ex instanceof ResponseStatusException;
            boolean serverFault = statusCode.value() == HttpStatus.INTERNAL_SERVER_ERROR.value();
            if (!serviceMessage || serverFault) {
                log.warn("{} answered with {}: {}", ex.getClass().getSimpleName(),
                        statusCode.value(), ex.getMessage());
                problem.setDetail(userMessageFor(statusCode));
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    /** Constraint violations on {@code @RequestParam} / {@code @PathVariable}. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fieldErrors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> lastPathNode(v),
                        ConstraintViolation::getMessage,
                        (a, b) -> a,
                        LinkedHashMap::new));

        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        body.setTitle("Validation failed");
        body.setDetail(summarise(fieldErrors));
        body.setProperty(ERRORS_PROPERTY, fieldErrors);
        return body;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        body.setTitle("Forbidden");
        body.setDetail("You do not have permission to perform this action.");
        return body;
    }

    /** Unique/FK violations — a conflict, not a server fault. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        body.setTitle("Conflict");
        body.setDetail("That change conflicts with existing data. "
                + "The record may already exist or is still referenced elsewhere.");
        return body;
    }

    /**
     * Last-resort handler. The cause is logged server-side; the client gets a
     * generic message so internals are never leaked.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        body.setTitle("Internal Server Error");
        body.setDetail("Something went wrong on our side. Please try again.");
        return body;
    }

    static String userMessageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "The request could not be understood. Please check what you entered and try again.";
            case 401 -> "Your session has expired. Please sign in again.";
            case 403 -> "You do not have permission to perform this action.";
            case 404 -> "The page or item you asked for does not exist.";
            case 405 -> "That action is not available here.";
            case 413 -> "The file is too large to upload.";
            case 415 -> "The upload was not sent in a format the server accepts. "
                    + "Please try again, and contact support if it keeps happening.";
            default -> status.is5xxServerError()
                    ? "Something went wrong on our side. Please try again."
                    : "The request could not be completed. Please try again.";
        };
    }

    private static String lastPathNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private static String summarise(Map<String, String> fieldErrors) {
        return fieldErrors.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .collect(Collectors.joining("; "));
    }
}
