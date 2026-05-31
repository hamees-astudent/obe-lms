package com.lms.modules.enrollment;

/**
 * Lightweight projection for batch-fetching student names for course rosters.
 * Used by {@link EnrollmentRepository#findStudentNamesByPscId}.
 */
interface StudentNameView {
    /** UUID of the enrolled user, returned as text from native SQL. */
    String getStudentId();
    String getStudentName();
}
