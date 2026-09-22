package com.lms.modules.exams;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A student's confirmed marks for one exam — the authoritative academic record.
 *
 * <p>Only a teacher's confirmation creates one of these. An {@link ExamScan}
 * holds what the extractor <em>read</em>; this holds what a person
 * <em>accepted</em>, which is why {@link #recordedBy} is always a user and
 * never the extractor.
 */
@Entity
@Table(name = "exam_results")
@Getter
@Setter
@NoArgsConstructor
public class ExamResult extends BaseEntity {

    /** FK → exams.id (same module) — plain UUID. */
    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    /** FK → users.id (users module) — plain UUID. */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** Sum of the per-question marks; denormalised for listing and transcripts. */
    @Column(name = "total_obtained", nullable = false, precision = 7, scale = 2)
    private BigDecimal totalObtained = BigDecimal.ZERO;

    /** SCAN | MANUAL — how these marks reached the system. */
    @Column(nullable = false, length = 10)
    private String source = SOURCE_MANUAL;

    public static final String SOURCE_SCAN   = "SCAN";
    public static final String SOURCE_MANUAL = "MANUAL";

    /** FK → exam_scans.id — the photographed copy, when {@code source = SCAN}. */
    @Column(name = "scan_id")
    private UUID scanId;

    /** FK → users.id — the teacher who confirmed these marks. */
    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt = LocalDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
