package com.lms.modules.exams.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A scanned copy, presented to the teacher for review.
 *
 * <p>Deliberately not a result: nothing here has been written to a student's
 * record yet. The teacher sees what was read, what it was matched to, and what
 * looked wrong, then confirms or corrects it.
 */
@Value
@Builder
public class ScanReviewResponse {

    UUID id;
    UUID examId;
    String examTitle;
    String status;

    /** Object key of the stored page, for the teacher to compare against on screen. */
    String imageKey;

    // ── As read off the page ────────────────────────────────────────────────

    String readRollNumber;
    String readStudentName;
    String readCourseCode;
    LocalDate readExamDate;

    /** The total the marker wrote on the page, if any. */
    java.math.BigDecimal readWrittenTotal;

    String confidence;

    /** The extractor's own notes about what it could not read. */
    List<String> extractorNotes;

    // ── What it was matched to ──────────────────────────────────────────────

    /** Null when the roll number matched no student on this offering's roster. */
    UUID matchedStudentId;
    String matchedStudentName;
    String matchedStudentNumber;

    /** One row per exam question, pre-filled with the mark read for it. */
    List<ProposedMark> proposedMarks;

    /** Sum of the proposed marks — compare against {@link #readWrittenTotal}. */
    java.math.BigDecimal proposedTotal;

    /**
     * Mismatches found while checking the reading against the exam. Advisory:
     * the teacher decides, these only say where to look.
     */
    List<String> warnings;

    String errorMessage;

    LocalDateTime createdAt;

    /**
     * One question's proposed mark.
     *
     * <p>{@code marksObtained} being null means the extractor could not read the
     * cell — the teacher fills it in. That is different from a confirmed zero.
     */
    @Value
    @Builder
    public static class ProposedMark {
        UUID questionId;
        String questionNo;
        java.math.BigDecimal maxMarks;
        java.math.BigDecimal marksObtained;

        /** True when nothing on the page could be matched to this question. */
        boolean unread;
    }
}
