package com.lms.modules.studyai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A question the teacher is writing, to suggest its CLO, Bloom level and marks. */
public record QuestionAssistRequest(
        @NotNull UUID pscId,
        @NotBlank String questionText,
        @NotNull @Pattern(regexp = "MCQ|MSQ|SHORT_ANSWER|LONG_ANSWER") String type,
        List<Map<String, Object>> options,
        List<String> correctAnswer,
        String modelAnswer,
        @Pattern(regexp = "EASY|MEDIUM|HARD") String difficulty
) {}
