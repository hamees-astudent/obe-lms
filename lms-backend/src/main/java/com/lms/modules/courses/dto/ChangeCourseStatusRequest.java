package com.lms.modules.courses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeCourseStatusRequest(
        @NotBlank
        @Pattern(regexp = "ACTIVE|INACTIVE")
        String status
) {}
