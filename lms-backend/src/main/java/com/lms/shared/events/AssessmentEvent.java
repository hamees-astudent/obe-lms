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
 *   <li>{@code ASSIGNMENT_CREATED}   — notify every enrolled student.</li>
 *   <li>{@code QUIZ_CREATED}         — notify every enrolled student.</li>
 *   <li>{@code MATERIAL_ADDED}       — notify every enrolled student.</li>
 *   <li>{@code ASSIGNMENT_SUBMITTED} — notify the teacher/assistant.</li>
 *   <li>{@code ASSIGNMENT_GRADED}    — notify the student.</li>
 *   <li>{@code QUIZ_SUBMITTED}       — notify the teacher/assistant.</li>
 * </ul>
 *
 * <p>The {@code *_CREATED} and {@code MATERIAL_ADDED} actions fan out to a whole
 * class, so they carry {@link #pscId} instead of a single student: the consumer
 * resolves the roster itself rather than the publisher emitting one event per
 * student.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentEvent {

    public enum Action {
        /** New work published to a class — fan-out to enrolled students. */
        ASSIGNMENT_CREATED,
        QUIZ_CREATED,
        MATERIAL_ADDED,

        /** Per-student lifecycle events. */
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

    // ── Student (per-student actions only) ───────────────────────────────────
    private UUID   studentId;
    private String studentEmail;
    private String studentName;

    // ── Class (fan-out actions only) ─────────────────────────────────────────
    /** Course offering whose roster should be notified. */
    private UUID   pscId;

    /** Free-form detail for the notification body, e.g. a due date or material type. */
    private String detail;

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
