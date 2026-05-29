package com.lms.modules.assessment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class GradeSubmissionRequest {

    @NotNull
    @DecimalMin("0")
    private BigDecimal marksObtained;

    private String feedback;
}
