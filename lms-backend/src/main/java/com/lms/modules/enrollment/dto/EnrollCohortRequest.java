package com.lms.modules.enrollment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Enroll every member of a cohort as a STUDENT of this offering. */
public record EnrollCohortRequest(
        @NotNull UUID pscId
) {}
