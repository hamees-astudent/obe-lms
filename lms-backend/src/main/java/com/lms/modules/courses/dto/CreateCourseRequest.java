package com.lms.modules.courses.dto;

import com.lms.shared.InstitutionalCodes;
import jakarta.validation.constraints.*;

public record CreateCourseRequest(

        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = InstitutionalCodes.CODE_PATTERN, message = InstitutionalCodes.CODE_MESSAGE)
        String code,

        @NotBlank @Size(max = 255)
        String name,

        String description,

        @Min(1) @Max(20)
        int creditHours
) {}
