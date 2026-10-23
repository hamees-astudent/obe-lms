package com.lms.modules.studyai.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The teacher's edits to a draft; every field is replaced. */
public record UpdateDraftRequest(
        @NotBlank String questionText,
        @NotNull List<Map<String, Object>> options,
        @NotNull List<String> correctAnswer,
        String modelAnswer,
        List<Map<String, Object>> rubric,
        String explanation,
        @NotNull @DecimalMin("0.5") BigDecimal marks,
        @Pattern(regexp = "REMEMBER|UNDERSTAND|APPLY|ANALYZE|EVALUATE|CREATE") String bloomLevel,
        @Pattern(regexp = "EASY|MEDIUM|HARD") String difficulty,
        @NotNull List<UUID> cloIds,
        @Pattern(regexp = "PENDING|ACCEPTED|REJECTED") String status
) {}
