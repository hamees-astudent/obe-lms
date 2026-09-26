package com.lms.modules.enrollment;

/**
 * Member count per cohort, for the cohort list.
 *
 * <p>Public on purpose — see {@link CohortMemberView}.
 */
public interface CohortSizeView {
    String getCohortId();
    long getMemberCount();
}
