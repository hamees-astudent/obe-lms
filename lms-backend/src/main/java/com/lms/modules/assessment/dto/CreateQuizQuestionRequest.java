package com.lms.modules.assessment.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class CreateQuizQuestionRequest {

    @NotBlank
    private String questionText;

    /** MCQ | MSQ */
    @NotBlank
    @Pattern(regexp = "MCQ|MSQ", message = "type must be MCQ or MSQ")
    private String type = "MCQ";

    /** Array of option objects: [{"id":"a","text":"Option A"}, ...] */
    @NotNull
    @Size(min = 2)
    private List<Map<String, Object>> options;

    /**
     * Correct option id(s): ["a"] for MCQ, ["a","c"] for MSQ.
     * Must have exactly one element for MCQ.
     */
    @NotNull
    @Size(min = 1)
    private List<String> correctAnswer;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal marks;

    @Min(0)
    private int orderIndex = 0;

    private String explanation;
}
