package com.lms.modules.enrollment;

/**
 * Lightweight projection for batch-fetching student identities for course rosters.
 * Used by {@link EnrollmentRepository#findStudentNamesByPscId}.
 *
 * <p>Public on purpose: Spring Data returns projections as JDK dynamic proxies,
 * and the proxy is defined in a {@code jdk.proxy*} module that cannot reach a
 * package-private type. Narrowing this back to package-private makes every
 * query that returns it fail at runtime with {@code IllegalAccessError}.
 */
public interface StudentNameView {
    /** UUID of the enrolled user, returned as text from native SQL. */
    String getStudentId();
    String getStudentName();
    /** Institutional roll number; null when the student has no profile row yet. */
    String getStudentNumber();
}
