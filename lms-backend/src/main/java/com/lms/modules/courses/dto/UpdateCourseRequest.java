package com.lms.modules.courses.dto;

import jakarta.validation.constraints.*;

public record UpdateCourseRequest(

        @NotBlank @Size(max = 255)
        String name,

        String description,

        @Min(1) @Max(20)
        int creditHours
) {}
