package com.lms.modules.attendance;

/**
 * Projection for enriching an {@link com.lms.shared.events.AttendanceAlertEvent}
 * without injecting cross-module repositories.
 */
interface AttendanceContextView {
    String getStudentEmail();
    String getStudentName();
    String getCourseCode();
    String getCourseName();
    String getSemesterName();
}
