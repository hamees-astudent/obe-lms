package com.lms.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a student's computed attendance percentage in a course drops
 * below the configurable threshold (default: 75 %).
 *
 * <p>Consumed by the {@code notifications} module to warn the student and
 * optionally notify the course teacher.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceAlertEvent {

    /** Unique event identifier — use for idempotent processing. */
    @Builder.Default
    private UUID eventId = UUID.randomUUID();

    @Builder.Default
    private Instant occurredAt = Instant.now();

    // ── Student ──────────────────────────────────────────────────────────────
    private UUID   studentId;
    private String studentEmail;
    private String studentName;

    // ── Course context ───────────────────────────────────────────────────────
    private UUID   programSemesterCourseId;
    private String courseCode;
    private String courseName;
    private String semesterName;

    // ── Attendance figures ────────────────────────────────────────────────────
    /** Computed attendance percentage at the time of the alert (0–100). */
    private double attendancePercentage;

    /** Configured threshold that was breached (e.g., 75.0). */
    private double thresholdPercentage;
}
