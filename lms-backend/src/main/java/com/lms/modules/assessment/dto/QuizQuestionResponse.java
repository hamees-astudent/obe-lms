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
public class QuizQuestionResponse {
    UUID id;
    UUID quizId;
    String questionText;
    String type;
    List<Map<String, Object>> options;
    /** Null when the caller is a student who has not yet submitted. */
    List<String> correctAnswer;
    BigDecimal marks;
    int orderIndex;
    String explanation;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
