package com.lms.modules.exams.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class CreateExamRequest {

    @NotNull
    private UUID pscId;

    @NotBlank
    @Size(max = 255)
    private String title;

    /** MIDTERM | FINAL | SESSIONAL | MAKEUP */
    @NotBlank
    @Pattern(regexp = "MIDTERM|FINAL|SESSIONAL|MAKEUP",
             message = "examType must be MIDTERM, FINAL, SESSIONAL, or MAKEUP")
    private String examType = "MIDTERM";

    /**
     * Not constrained to the past: exams are normally set up before they are
     * sat, and the marks arrive afterwards.
     */
    @NotNull
    private LocalDate examDate;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal totalMarks;

    /**
     * The marks table's rows. Optional at creation — an exam starts in DRAFT and
     * the questions can be added before it is opened for marking.
     */
    @Valid
    private List<ExamQuestionRequest> questions = new ArrayList<>();
}
