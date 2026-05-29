package com.lms.modules.assessment.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class QuizResponse {
    UUID id;
    UUID pscId;
    UUID createdBy;
    String title;
    String description;
    Integer durationMinutes;
    BigDecimal totalMarks;
    LocalDateTime availableFrom;
    LocalDateTime availableUntil;
    boolean shuffleQuestions;
    boolean shuffleOptions;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
