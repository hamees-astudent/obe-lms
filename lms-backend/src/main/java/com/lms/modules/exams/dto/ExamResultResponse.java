package com.lms.modules.exams.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class ExamResultResponse {

    UUID id;
    UUID examId;
    UUID studentId;
    String studentName;
    String studentNumber;

    BigDecimal totalObtained;
    BigDecimal totalMarks;
    Double percentage;

    /** SCAN | MANUAL */
    String source;

    /** The photographed copy behind these marks, when source is SCAN. */
    UUID scanId;

    UUID recordedBy;
    LocalDateTime recordedAt;
    String remarks;

    List<QuestionMarkResponse> marks;

    @Value
    @Builder
    public static class QuestionMarkResponse {
        UUID questionId;
        String questionNo;
        BigDecimal maxMarks;
        BigDecimal marksObtained;
    }
}
