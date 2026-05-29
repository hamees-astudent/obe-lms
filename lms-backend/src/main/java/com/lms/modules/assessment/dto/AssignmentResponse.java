package com.lms.modules.assessment.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class AssignmentResponse {
    UUID id;
    UUID pscId;
    UUID createdBy;
    String title;
    String description;
    String submissionType;
    BigDecimal totalMarks;
    LocalDateTime dueDate;
    boolean allowLateSubmission;
    BigDecimal latePenaltyPercent;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
