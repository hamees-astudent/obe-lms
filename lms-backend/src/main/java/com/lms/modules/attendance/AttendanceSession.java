package com.lms.modules.attendance;

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
 * One class meeting opened by a teacher or assistant.
 * A session is "open" while {@code closedAt} is null;
 * attendance records are only created against open sessions.
 */
@Entity
@Table(name = "attendance_sessions")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AttendanceSession extends BaseEntity {

    /** FK → program_semester_courses.id (courses module) — plain UUID. */
    @Column(name = "psc_id", nullable = false)
    private UUID pscId;

    /** FK → users.id (users module) — plain UUID. */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(length = 255)
    private String topic;

    /** Timestamp when the teacher explicitly opened the session (defaults to now). */
    @Column(name = "opened_at", nullable = false, updatable = false)
    private LocalDateTime openedAt = LocalDateTime.now();

    /** Null while the session is open; set on close. */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isOpen() {
        return closedAt == null;
    }
}
