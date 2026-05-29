package com.lms.modules.assessment;

/**
 * Native-query projection for enriching {@link com.lms.shared.events.AssessmentEvent}
 * with student and course details without crossing module boundaries.
 */
interface AssessmentContextView {
    String getStudentEmail();
    String getStudentName();
    String getCourseCode();
    String getCourseName();
}
