package com.lms.modules.enrollment;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One enrollment row per student per {@code program_semester_course}.
 *
 * <p>Status lifecycle: {@code ACTIVE} → {@code DROPPED} (manual drop) or
 * {@code COMPLETED} (semester close, driven by the transcript module).</p>
 */
@Entity
@Table(name = "enrollments")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Enrollment extends BaseEntity {

    /** FK → program_semester_courses.id (courses module) — plain UUID. */
    @Column(name = "psc_id", nullable = false)
    private UUID pscId;

    /** FK → users.id (users module) — plain UUID. */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** ACTIVE | DROPPED | COMPLETED */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    /** Set when status transitions to DROPPED. */
    @Column(name = "dropped_at")
    private LocalDateTime droppedAt;

    /**
     * Timestamp of enrollment activation (may differ from {@code createdAt}
     * if a previously-dropped student re-enrolls — the row is reused and
     * {@code enrolledAt} is updated to now).
     */
    @Column(name = "enrolled_at", nullable = false)
    private LocalDateTime enrolledAt = LocalDateTime.now();

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
