package com.lms.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an assessment lifecycle event occurs:
 * assignment submitted, assignment graded, or quiz submitted.
 *
 * <p>Consumed by the {@code notifications} module to:
 * <ul>
 *   <li>{@code ASSIGNMENT_SUBMITTED} — notify the teacher/assistant.</li>
 *   <li>{@code ASSIGNMENT_GRADED}    — notify the student.</li>
 *   <li>{@code QUIZ_SUBMITTED}       — notify the teacher/assistant.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentEvent {

    public enum Action {
        ASSIGNMENT_SUBMITTED,
        ASSIGNMENT_GRADED,
        QUIZ_SUBMITTED
    }

    /** Unique event identifier — use for idempotent processing. */
    @Builder.Default
    private UUID eventId = UUID.randomUUID();

    @Builder.Default
    private Instant occurredAt = Instant.now();

    private Action action;

    // ── Student ──────────────────────────────────────────────────────────────
    private UUID   studentId;
    private String studentEmail;
    private String studentName;

    // ── Assessment context ───────────────────────────────────────────────────
    /** {@code assignments.id} or {@code quizzes.id} depending on {@link Action}. */
    private UUID   assessmentId;
    private String assessmentTitle;
    private String courseCode;
    private String courseName;

    // ── Grading payload (non-null only for ASSIGNMENT_GRADED) ─────────────────
    private Double marksObtained;
    private Double totalMarks;
    private String feedback;
}
