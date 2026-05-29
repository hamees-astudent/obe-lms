package com.lms.modules.programs.dto;

import jakarta.validation.constraints.*;

public record CreateProgramRequest(

        @NotBlank @Size(max = 180)
        String name,

        @NotBlank @Size(max = 20)
        @Pattern(regexp = "[A-Z0-9_]+", message = "Code must be uppercase letters, digits or underscores")
        String code,

        String description,

        @Min(1) @Max(10)
        int durationYears
) {}
