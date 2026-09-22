package com.lms.modules.exams.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Manual mark entry, for copies that are not scanned. */
@Data
public class RecordMarksRequest {

    @NotNull
    private UUID studentId;

    @Valid
    @NotEmpty(message = "At least one question mark is required")
    private List<QuestionMarkEntry> marks;

    private String remarks;
}
