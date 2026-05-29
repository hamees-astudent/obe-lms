package com.lms.modules.enrollment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Student self-enrollment request — studentId is resolved from the security principal. */
public record SelfEnrollRequest(
        @NotNull UUID pscId
) {}
