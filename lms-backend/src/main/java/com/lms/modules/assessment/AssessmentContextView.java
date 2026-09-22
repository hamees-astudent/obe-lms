package com.lms.modules.assessment;

/**
 * Native-query projection for enriching {@link com.lms.shared.events.AssessmentEvent}
 * with student and course details without crossing module boundaries.
 *
 * <p>Public on purpose: Spring Data returns projections as JDK dynamic proxies,
 * and the proxy is defined in a {@code jdk.proxy*} module that cannot reach a
 * package-private type. Narrowing this back to package-private makes every
 * query that returns it fail at runtime with {@code IllegalAccessError}.
 */
public interface AssessmentContextView {
    String getStudentEmail();
    String getStudentName();
    String getCourseCode();
    String getCourseName();
}
