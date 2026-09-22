package com.lms.modules.exams.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class QuestionMarkEntry {

    @NotNull
    private UUID questionId;

    /**
     * Null records the question as not attempted. The upper bound is not
     * declared here — it is the question's own {@code maxMarks}, checked by the
     * service, since a single annotation cannot know it.
     */
    @DecimalMin(value = "0", message = "A mark cannot be negative")
    private BigDecimal marksObtained;
}
