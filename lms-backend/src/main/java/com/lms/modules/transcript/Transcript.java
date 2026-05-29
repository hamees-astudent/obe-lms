package com.lms.modules.transcript;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable transcript record generated once per student per semester on semester closure.
 * Re-generation replaces the row (delete + insert) while preserving the audit via
 * {@code generated_at} / {@code generated_by}.
 */
@Entity
@Table(name = "transcripts")
@Getter
@Setter
@NoArgsConstructor
public class Transcript extends BaseEntity {

    /** FK → users.id (users module) — plain UUID. */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** FK → semesters.id (programs module) — plain UUID. */
    @Column(name = "semester_id", nullable = false)
    private UUID semesterId;

    /** FK → programs.id (programs module) — plain UUID. */
    @Column(name = "program_id", nullable = false)
    private UUID programId;

    /** FK → grading_scales.id (same module) — plain UUID. */
    @Column(name = "grading_scale_id", nullable = false)
    private UUID gradingScaleId;

    /**
     * Full immutable snapshot written at semester closure.
     * Shape documented in V0016 migration.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> snapshot;

    @Column(name = "semester_gpa", nullable = false, precision = 4, scale = 2)
    private BigDecimal semesterGpa;

    @Column(name = "cumulative_gpa", nullable = false, precision = 4, scale = 2)
    private BigDecimal cumulativeGpa;

    @Column(name = "total_credit_hours", nullable = false)
    private int totalCreditHours;

    @Column(name = "earned_credit_hours", nullable = false)
    private int earnedCreditHours;

    /** Timestamp the snapshot was generated; updated on re-generation. */
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    /** FK → users.id — the admin who closed the semester. */
    @Column(name = "generated_by", nullable = false)
    private UUID generatedBy;
}
