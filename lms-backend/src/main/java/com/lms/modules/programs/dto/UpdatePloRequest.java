package com.lms.modules.programs.dto;

import jakarta.validation.constraints.*;

public record UpdatePloRequest(

        @NotBlank @Size(max = 20)
        String code,

        @NotBlank @Size(max = 255)
        String title,

        String description,

        @Min(1)
        int orderIndex
) {}
