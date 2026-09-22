package com.lms.modules.exams.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ChangeExamStatusRequest {

    /** DRAFT | OPEN | LOCKED */
    @NotBlank
    @Pattern(regexp = "DRAFT|OPEN|LOCKED", message = "status must be DRAFT, OPEN, or LOCKED")
    private String status;
}
