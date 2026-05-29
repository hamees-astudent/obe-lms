package com.lms.modules.enrollment;

/**
 * Projection returned by the native event-context query.
 * Used to populate {@link com.lms.shared.events.EnrollmentEvent} without
 * cross-module repository injections.
 */
interface EnrollmentContextView {
    String getStudentEmail();
    String getStudentName();
    String getCourseCode();
    String getCourseName();
    String getSemesterName();
    String getProgramName();
}
