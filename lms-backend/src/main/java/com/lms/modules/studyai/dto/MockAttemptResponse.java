package com.lms.modules.studyai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MockAttemptResponse(
        UUID id,
        UUID pscId,
        /** QUIZ | PAPER */
        String kind,
        /** GENERATING | READY | GRADED | FAILED */
        String status,
        String error,
        Map<String, Object> config,
        BigDecimal totalMarks,
        BigDecimal score,
        int questionCount,
        LocalDateTime createdAt,
        LocalDateTime submittedAt,
        /** Present when one attempt is fetched. */
        List<MockQuestionResponse> questions,
        Map<String, List<String>> answers,
        /** Per question once graded: score, maxMarks, feedback, correct, method, covered, missing. */
        Map<String, Map<String, Object>> results
) {}
