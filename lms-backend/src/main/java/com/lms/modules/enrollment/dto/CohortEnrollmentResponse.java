package com.lms.modules.enrollment.dto;

import java.util.List;
import java.util.UUID;

public record CohortEnrollmentResponse(
        UUID cohortId,
        UUID pscId,
        int enrolled,
        int skipped,
        /** One entry per cohort member, in roll-number order. */
        List<Result> results
) {
    public static final String ENROLLED = "ENROLLED";
    public static final String SKIPPED  = "SKIPPED";

    public record Result(
            UUID studentId,
            String studentName,
            String studentNumber,
            /** {@link #ENROLLED} or {@link #SKIPPED}. */
            String outcome,
            /** Why the student was skipped; null when enrolled. */
            String reason
    ) {}
}
