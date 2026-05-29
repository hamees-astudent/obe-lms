package com.lms.modules.assessment.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class AssignmentSubmissionResponse {
    UUID id;
    UUID assignmentId;
    UUID studentId;
    String status;
    String textContent;
    String fileKey;
    String fileName;
    Long fileSize;
    LocalDateTime submittedAt;
    BigDecimal marksObtained;
    String feedback;
    UUID gradedBy;
    LocalDateTime gradedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
