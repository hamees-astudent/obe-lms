package com.lms.modules.courses.dto;

import jakarta.validation.constraints.*;

public record CreateCourseRequest(

        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "[A-Z0-9_]+", message = "Code must be uppercase letters, digits or underscores")
        String code,

        @NotBlank @Size(max = 255)
        String name,

        String description,

        @Min(1) @Max(20)
        int creditHours
) {}
