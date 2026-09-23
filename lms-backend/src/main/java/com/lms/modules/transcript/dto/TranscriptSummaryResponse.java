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
    /** Display fields the UI reads (dashboard, transcript cards); from the snapshot. */
    String        studentName;
    String        semesterName;
    String        programName;
    /** Same values as semesterGpa / cumulativeGpa under the names the UI uses. */
    BigDecimal    sgpa;
    BigDecimal    cgpa;
    int           totalCreditHours;
    int           earnedCreditHours;
    LocalDateTime generatedAt;
    LocalDateTime createdAt;
}
