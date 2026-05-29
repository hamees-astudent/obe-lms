package com.lms.modules.programs;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An academic term within a program (e.g. "Fall 2026").
 *
 * <p>Status lifecycle: {@code OPEN} → {@code CLOSED} (→ {@code OPEN} via reopen).
 * Only one semester per program may be OPEN at any time (enforced by a partial
 * unique index in the DB).</p>
 *
 * <p>Closing a semester publishes a {@link com.lms.shared.events.SemesterEvent}
 * that triggers transcript generation in the transcript module.</p>
 */
@Entity
@Table(name = "semesters")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Semester extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** OPEN | CLOSED */
    @Column(nullable = false, length = 10)
    private String status = "OPEN";

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** UUID of the admin user who closed the semester — plain FK, no ORM join. */
    @Column(name = "closed_by")
    private UUID closedById;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
