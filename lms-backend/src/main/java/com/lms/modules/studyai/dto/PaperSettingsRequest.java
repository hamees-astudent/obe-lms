package com.lms.modules.studyai.dto;

import com.lms.modules.studyai.MarksPolicy;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PaperSettingsRequest(
        /** No generated paper may carry more marks than this. */
        @NotNull @DecimalMin("1") @DecimalMax("1000") BigDecimal maxTotalMarks,
        @Min(1) @Max(200) int maxQuestions,
        @Min(5) @Max(600) int maxDurationMinutes,
        /** Cap on the mock papers students generate for practice. */
        @NotNull @DecimalMin("1") @DecimalMax("1000") BigDecimal mockMaxTotalMarks,
        /** Null keeps the built-in table. */
        MarksPolicy marksTable
) {}
