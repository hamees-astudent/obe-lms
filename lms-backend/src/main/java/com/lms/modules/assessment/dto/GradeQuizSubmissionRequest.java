package com.lms.modules.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The teacher's marks for the written-answer questions of one attempt. Choice
 * questions keep the auto-grader's score.
 */
@Data
public class GradeQuizSubmissionRequest {

    /** Question id → marks and feedback, one entry per written-answer question. */
    @NotEmpty
    private Map<String, @Valid QuestionGrade> grades;

    @Data
    public static class QuestionGrade {
        @NotNull
        @DecimalMin("0")
        private BigDecimal score;
        private String feedback;
    }
}
