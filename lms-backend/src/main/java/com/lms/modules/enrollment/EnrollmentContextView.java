package com.lms.modules.enrollment;

/**
 * Projection returned by the native event-context query.
 * Used to populate {@link com.lms.shared.events.EnrollmentEvent} without
 * cross-module repository injections.
 *
 * <p>Public on purpose: Spring Data returns projections as JDK dynamic proxies,
 * and the proxy is defined in a {@code jdk.proxy*} module that cannot reach a
 * package-private type. Narrowing this back to package-private makes every
 * query that returns it fail at runtime with {@code IllegalAccessError}.
 */
public interface EnrollmentContextView {
    String getStudentEmail();
    String getStudentName();
    String getCourseCode();
    String getCourseName();
    String getSemesterName();
    String getProgramName();
}
