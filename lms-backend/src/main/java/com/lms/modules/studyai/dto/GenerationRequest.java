package com.lms.modules.studyai.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the teacher wants generated. */
public record GenerationRequest(
        /** Materials to draw on; empty with no topics means every indexed material. */
        List<UUID> materialIds,
        /** Topics to draw on; takes precedence over materialIds. */
        List<UUID> topicIds,
        /** Questions per type: MCQ, MSQ, SHORT_ANSWER, LONG_ANSWER. */
        @NotNull Map<String, Integer> counts,
        @Pattern(regexp = "EASY|MEDIUM|HARD") String difficulty,
        List<@Pattern(regexp = "REMEMBER|UNDERSTAND|APPLY|ANALYZE|EVALUATE|CREATE") String> bloomLevels,
        /** CLOs the questions should assess; empty means any of the course's CLOs. */
        List<UUID> targetCloIds,
        /** The paper's marks limit — never above the offering's maximum. */
        @NotNull @DecimalMin("1") BigDecimal maxTotalMarks,
        @Size(max = 1000) String instructions
) {}
