package com.lms.modules.studyai.dto;

import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A practice quiz or paper a student asks for. */
public record MockRequest(
        @NotNull UUID pscId,
        @NotNull @Pattern(regexp = "QUIZ|PAPER") String kind,
        List<UUID> topicIds,
        List<UUID> materialIds,
        /** Questions per type: MCQ, MSQ, SHORT_ANSWER, LONG_ANSWER. */
        @NotNull Map<String, Integer> counts,
        @Pattern(regexp = "EASY|MEDIUM|HARD") String difficulty,
        @Positive @Max(300) Integer durationMinutes
) {}
