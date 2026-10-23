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
    /** Submitter identity; populated on staff-facing responses only. */
    String studentName;
    String studentEmail;
    String studentNumber;
    Map<String, List<String>> answers;
    LocalDateTime startedAt;
    /** Seconds left on a timed attempt still in progress; absent otherwise. */
    Long remainingSeconds;
    LocalDateTime submittedAt;
    BigDecimal score;
    boolean autoGraded;
    /** IN_PROGRESS | PENDING_REVIEW | GRADED. */
    String gradingStatus;
    /** Per-question score and feedback: {"<question-id>": {"score": 2, "feedback": "..."}}. */
    Map<String, Map<String, Object>> questionScores;
    LocalDateTime gradedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
