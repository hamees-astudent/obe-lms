package com.lms.modules.exams.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class UpdateExamRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Pattern(regexp = "MIDTERM|FINAL|SESSIONAL|MAKEUP",
             message = "examType must be MIDTERM, FINAL, SESSIONAL, or MAKEUP")
    private String examType;

    @NotNull
    private LocalDate examDate;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal totalMarks;

    /**
     * When present, replaces the whole question list. Omit to leave the
     * questions untouched while editing the exam's own details.
     */
    @Valid
    private List<ExamQuestionRequest> questions;
}
