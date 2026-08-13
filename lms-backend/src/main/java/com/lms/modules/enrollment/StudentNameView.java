package com.lms.modules.enrollment;

/**
 * Lightweight projection for batch-fetching student identities for course rosters.
 * Used by {@link EnrollmentRepository#findStudentNamesByPscId}.
 */
interface StudentNameView {
    /** UUID of the enrolled user, returned as text from native SQL. */
    String getStudentId();
    String getStudentName();
    /** Institutional roll number; null when the student has no profile row yet. */
    String getStudentNumber();
}
