package com.lms.modules.programs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeProgramStatusRequest(
        @NotBlank
        @Pattern(regexp = "ACTIVE|INACTIVE")
        String status
) {}
