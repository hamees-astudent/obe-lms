package com.lms.modules.assessment.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class UpdateQuizQuestionRequest {

    @NotBlank
    private String questionText;

    @NotBlank
    @Pattern(regexp = "MCQ|MSQ|SHORT_ANSWER|LONG_ANSWER",
             message = "type must be MCQ, MSQ, SHORT_ANSWER or LONG_ANSWER")
    private String type;

    @NotNull
    private List<Map<String, Object>> options = List.of();

    @NotNull
    private List<String> correctAnswer = List.of();

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal marks;

    @Min(0)
    private int orderIndex;

    private String explanation;

    @Pattern(regexp = "REMEMBER|UNDERSTAND|APPLY|ANALYZE|EVALUATE|CREATE",
             message = "bloomLevel must be a Bloom's taxonomy level")
    private String bloomLevel;

    @Pattern(regexp = "EASY|MEDIUM|HARD", message = "difficulty must be EASY, MEDIUM or HARD")
    private String difficulty;

    private String modelAnswer;

    private List<Map<String, Object>> rubric = List.of();

    /** CLOs this question assesses; null leaves existing mappings untouched. */
    private List<UUID> cloIds;
}
