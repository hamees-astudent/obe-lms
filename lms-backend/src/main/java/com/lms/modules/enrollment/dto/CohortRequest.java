package com.lms.modules.enrollment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create or rename a cohort. */
public record CohortRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description
) {}
