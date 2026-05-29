package com.lms.modules.attendance;

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
 * One attendance record per student per session.
 *
 * <p>Status semantics:
 * <ul>
 *   <li>PRESENT / LATE — count toward numerator AND denominator</li>
 *   <li>ABSENT       — denominator only</li>
 *   <li>EXCUSED      — excluded from both (no effect on percentage)</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "attendance_records")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AttendanceRecord extends BaseEntity {

    /** FK → attendance_sessions.id — plain UUID (same module). */
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /** FK → users.id (users module) — plain UUID. */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** PRESENT | ABSENT | LATE | EXCUSED */
    @Column(nullable = false, length = 10)
    private String status;

    /** The user (teacher/assistant) who created or last updated this record. */
    @Column(name = "marked_by", nullable = false)
    private UUID markedBy;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
