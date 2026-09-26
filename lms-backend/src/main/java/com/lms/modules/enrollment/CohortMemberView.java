package com.lms.modules.enrollment;

/**
 * A user as seen by the cohort screens: identity plus the role and account
 * status that decide whether they can be a member or be enrolled.
 *
 * <p>Public on purpose: Spring Data returns projections as JDK dynamic proxies,
 * and the proxy is defined in a {@code jdk.proxy*} module that cannot reach a
 * package-private type. Narrowing this back to package-private makes every
 * query that returns it fail at runtime with {@code IllegalAccessError}.
 */
public interface CohortMemberView {
    /** UUID of the user, returned as text from native SQL. */
    String getStudentId();
    String getName();
    String getEmail();
    /** Institutional roll number; null when the student has no profile row yet. */
    String getStudentNumber();
    String getRole();
    String getStatus();
}
