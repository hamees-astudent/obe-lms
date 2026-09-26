package com.lms.modules.enrollment.dto;

import java.util.UUID;

public record CohortMemberResponse(
        UUID studentId,
        String name,
        String email,
        /** Institutional roll number; null when the student has no profile row yet. */
        String studentNumber,
        /** Account status — an INACTIVE or SUSPENDED member is skipped when the cohort is enrolled. */
        String status
) {}
