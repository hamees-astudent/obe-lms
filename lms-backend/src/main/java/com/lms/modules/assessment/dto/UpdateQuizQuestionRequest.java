package com.lms.modules.assessment.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class UpdateQuizQuestionRequest {

    @NotBlank
    private String questionText;

    @NotBlank
    @Pattern(regexp = "MCQ|MSQ", message = "type must be MCQ or MSQ")
    private String type;

    @NotNull
    @Size(min = 2)
    private List<Map<String, Object>> options;

    @NotNull
    @Size(min = 1)
    private List<String> correctAnswer;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal marks;

    @Min(0)
    private int orderIndex;

    private String explanation;
}
