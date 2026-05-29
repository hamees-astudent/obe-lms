package com.lms.modules.assessment.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
@Builder
public class QuizSubmissionResponse {
    UUID id;
    UUID quizId;
    UUID studentId;
    Map<String, List<String>> answers;
    LocalDateTime startedAt;
    LocalDateTime submittedAt;
    BigDecimal score;
    boolean autoGraded;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
