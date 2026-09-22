package com.lms.modules.exams.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ExamQuestionRequest {

    /** The label as printed on the copy, e.g. {@code 1}, {@code 3a}. */
    @NotBlank
    @Size(max = 10)
    private String questionNo;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal maxMarks;

    /** CLOs this question assesses; drives OBE attainment. */
    @Valid
    private List<CloMappingRequest> cloMappings = new ArrayList<>();
}
