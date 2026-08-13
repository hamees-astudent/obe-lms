package com.lms.modules.programs.dto;

import com.lms.shared.InstitutionalCodes;
import jakarta.validation.constraints.*;

public record CreateProgramRequest(

        @NotBlank @Size(max = 180)
        String name,

        @NotBlank @Size(max = 20)
        @Pattern(regexp = InstitutionalCodes.CODE_PATTERN, message = InstitutionalCodes.CODE_MESSAGE)
        String code,

        String description,

        @Min(1) @Max(10)
        int durationYears
) {}
