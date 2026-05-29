package com.lms.modules.transcript.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class GradingScaleEntryResponse {
    UUID       id;
    UUID       scaleId;
    String     gradeLetter;
    BigDecimal minPercentage;
    BigDecimal maxPercentage;
    BigDecimal gradePoints;
    int        orderIndex;
    LocalDateTime createdAt;
}
