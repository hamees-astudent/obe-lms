package com.lms.modules.assessment.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CreateAssignmentRequest {

    @NotNull
    private UUID pscId;

    @NotBlank
    @Size(max = 255)
    private String title;

    private String description;

    /** FILE | TEXT | BOTH */
    @NotBlank
    @Pattern(regexp = "FILE|TEXT|BOTH", message = "submissionType must be FILE, TEXT, or BOTH")
    private String submissionType = "FILE";

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal totalMarks;

    @NotNull
    @Future
    private LocalDateTime dueDate;

    private boolean allowLateSubmission = false;

    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal latePenaltyPercent;
}
