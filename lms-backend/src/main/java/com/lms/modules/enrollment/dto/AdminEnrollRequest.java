package com.lms.modules.enrollment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/** Admin-only request: enroll any user in any offering with a course-specific role. */
public record AdminEnrollRequest(
        @NotNull UUID pscId,
        @NotNull UUID studentId,
        @NotBlank
        @Pattern(regexp = "TEACHER|ASSISTANT|STUDENT|ADMIN",
                 message = "courseRole must be one of: TEACHER, ASSISTANT, STUDENT, ADMIN")
        String courseRole
) {}
