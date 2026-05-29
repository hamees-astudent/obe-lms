package com.lms.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a student is enrolled in or dropped from a
 * {@code program_semester_course}.
 *
 * <p>Consumed by the {@code notifications} module to send confirmation emails
 * and in-app alerts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentEvent {

    public enum Action { ENROLLED, DROPPED }

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

    // ── Course context ───────────────────────────────────────────────────────
    private UUID   programSemesterCourseId;
    private String courseCode;
    private String courseName;
    private String semesterName;
    private String programName;
}
