package com.lms.modules.assessment;

/**
 * Who a submission belongs to, for staff views of assignment and quiz
 * submissions. Used by {@link AssignmentSubmissionRepository#findSubmitters}.
 *
 * <p>Public for the same reason as the enrollment module's
 * {@code StudentNameView}: Spring Data projection proxies live in a
 * {@code jdk.proxy*} module that cannot reach a package-private type.
 */
public interface SubmitterView {
    /** UUID of the student, returned as text from native SQL. */
    String getStudentId();
    String getStudentName();
    String getStudentEmail();
    /** Institutional roll number; null when the student has no profile row yet. */
    String getStudentNumber();
}
