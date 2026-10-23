package com.lms.modules.studyai.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PaperRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 4000) String instructions,
        @Positive Integer durationMinutes,
        /** This paper's marks limit; at most the offering's maximum. */
        @NotNull @DecimalMin("1") BigDecimal maxTotalMarks
) {}
