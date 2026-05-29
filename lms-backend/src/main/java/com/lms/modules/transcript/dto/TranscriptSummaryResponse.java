package com.lms.modules.transcript.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class TranscriptSummaryResponse {
    UUID          id;
    UUID          studentId;
    UUID          semesterId;
    UUID          programId;
    BigDecimal    semesterGpa;
    BigDecimal    cumulativeGpa;
    int           totalCreditHours;
    int           earnedCreditHours;
    LocalDateTime generatedAt;
    LocalDateTime createdAt;
}
