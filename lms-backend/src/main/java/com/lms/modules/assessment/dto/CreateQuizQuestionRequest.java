package com.lms.modules.assessment.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class CreateQuizQuestionRequest {

    @NotBlank
    private String questionText;

    /** MCQ | MSQ | SHORT_ANSWER | LONG_ANSWER */
    @NotBlank
    @Pattern(regexp = "MCQ|MSQ|SHORT_ANSWER|LONG_ANSWER",
             message = "type must be MCQ, MSQ, SHORT_ANSWER or LONG_ANSWER")
    private String type = "MCQ";

    /**
     * Array of option objects: [{"id":"a","text":"Option A"}, ...].
     * At least two for MCQ/MSQ; empty for written answers.
     */
    @NotNull
    private List<Map<String, Object>> options = List.of();

    /**
     * Correct option id(s): ["a"] for MCQ, ["a","c"] for MSQ, empty for
     * written answers.
     */
    @NotNull
    private List<String> correctAnswer = List.of();

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal marks;

    @Min(0)
    private int orderIndex = 0;

    private String explanation;

    /** REMEMBER | UNDERSTAND | APPLY | ANALYZE | EVALUATE | CREATE */
    @Pattern(regexp = "REMEMBER|UNDERSTAND|APPLY|ANALYZE|EVALUATE|CREATE",
             message = "bloomLevel must be a Bloom's taxonomy level")
    private String bloomLevel;

    @Pattern(regexp = "EASY|MEDIUM|HARD", message = "difficulty must be EASY, MEDIUM or HARD")
    private String difficulty;

    /** Required for written answers; what a full-marks answer says. */
    private String modelAnswer;

    /** Written answers: [{"criterion":"...","marks":2,"keywords":["..."]}]. */
    private List<Map<String, Object>> rubric = List.of();

    /** CLOs this question assesses; null leaves existing mappings untouched. */
    private List<UUID> cloIds;

    /** Set when the question came from the question generator. */
    private boolean aiGenerated;
}
