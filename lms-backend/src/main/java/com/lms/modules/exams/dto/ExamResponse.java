package com.lms.modules.exams.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class ExamResponse {
    UUID id;
    UUID pscId;
    String courseCode;
    String courseName;
    UUID createdBy;
    String title;
    String examType;
    LocalDate examDate;
    BigDecimal totalMarks;
    String status;
    List<ExamQuestionResponse> questions;

    /** Sum of the question maxima, so a mismatch with totalMarks is visible. */
    BigDecimal questionMarksTotal;

    /** How many students already have confirmed marks. */
    long resultsRecorded;

    /** How many students are on the offering's roster. */
    long rosterSize;

    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
