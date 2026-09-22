package com.lms.modules.exams.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * The teacher's confirmation of a scanned copy — what actually gets recorded.
 *
 * <p>The marks are sent back in full rather than as a diff against the
 * extraction. The teacher is the author of the record, so what they submit is
 * what is stored; nothing is carried over implicitly from the reading.
 */
@Data
public class ConfirmScanRequest {

    /**
     * The student these marks belong to. Required even when the roll number
     * matched, because confirming the identity is the point of the review.
     */
    @NotNull
    private UUID studentId;

    @Valid
    @NotEmpty(message = "At least one question mark is required")
    private List<QuestionMarkEntry> marks;

    private String remarks;
}
