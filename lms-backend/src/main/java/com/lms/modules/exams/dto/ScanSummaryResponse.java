package com.lms.modules.exams.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

/** A scan as it appears in the exam's review queue. */
@Value
@Builder
public class ScanSummaryResponse {
    UUID id;
    UUID examId;
    String status;
    String imageKey;
    String readRollNumber;
    String readStudentName;
    UUID matchedStudentId;
    String matchedStudentName;
    int warningCount;
    String errorMessage;
    LocalDateTime createdAt;
    LocalDateTime confirmedAt;
}
