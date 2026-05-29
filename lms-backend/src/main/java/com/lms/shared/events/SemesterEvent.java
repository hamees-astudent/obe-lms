package com.lms.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a semester's status changes (CLOSED or REOPENED).
 *
 * <p>Consumed by the {@code transcript} module to trigger snapshot generation
 * when {@link Action#CLOSED}, and by the {@code notifications} module to
 * alert students/teachers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemesterEvent {

    public enum Action { CLOSED, REOPENED }

    /** Unique event identifier — use for idempotent processing. */
    @Builder.Default
    private UUID eventId = UUID.randomUUID();

    @Builder.Default
    private Instant occurredAt = Instant.now();

    private Action action;

    // ── Semester context ─────────────────────────────────────────────────────
    private UUID   semesterId;
    private String semesterName;

    // ── Program context ───────────────────────────────────────────────────────
    private UUID   programId;
    private String programName;

    /** Email of the admin who triggered the status change. */
    private String triggeredByEmail;
}
