package com.lms.modules.attendance;

/**
 * Projection for enriching an {@link com.lms.shared.events.AttendanceAlertEvent}
 * without injecting cross-module repositories.
 *
 * <p>Public on purpose: Spring Data returns projections as JDK dynamic proxies,
 * and the proxy is defined in a {@code jdk.proxy*} module that cannot reach a
 * package-private type. Narrowing this back to package-private makes every
 * query that returns it fail at runtime with {@code IllegalAccessError}.
 */
public interface AttendanceContextView {
    String getStudentEmail();
    String getStudentName();
    String getCourseCode();
    String getCourseName();
    String getSemesterName();
}
