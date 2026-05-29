package com.lms.modules.assessment.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UpdateAssignmentRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    private String description;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal totalMarks;

    @NotNull
    private LocalDateTime dueDate;

    private boolean allowLateSubmission = false;

    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal latePenaltyPercent;
}
