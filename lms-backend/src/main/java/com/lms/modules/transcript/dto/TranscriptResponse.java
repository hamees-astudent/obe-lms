package com.lms.modules.transcript.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class TranscriptResponse {
    UUID                  id;
    UUID                  studentId;
    UUID                  semesterId;
    UUID                  programId;
    UUID                  gradingScaleId;
    BigDecimal            semesterGpa;
    BigDecimal            cumulativeGpa;
    int                   totalCreditHours;
    int                   earnedCreditHours;
    LocalDateTime         generatedAt;
    UUID                  generatedBy;
    LocalDateTime         createdAt;
    TranscriptSnapshotData snapshot;
}
