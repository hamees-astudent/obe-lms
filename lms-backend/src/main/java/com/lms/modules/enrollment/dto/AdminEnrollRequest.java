package com.lms.modules.enrollment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Admin-only request: enroll any student in any offering. */
public record AdminEnrollRequest(
        @NotNull UUID pscId,
        @NotNull UUID studentId
) {}
