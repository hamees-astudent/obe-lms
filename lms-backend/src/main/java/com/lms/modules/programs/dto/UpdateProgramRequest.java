package com.lms.modules.programs.dto;

import jakarta.validation.constraints.*;

public record UpdateProgramRequest(

        @NotBlank @Size(max = 180)
        String name,

        String description,

        @Min(1) @Max(10)
        int durationYears
) {}
