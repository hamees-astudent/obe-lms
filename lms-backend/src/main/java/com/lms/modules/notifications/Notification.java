package com.lms.modules.notifications;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {

    /** FK → users.id — the recipient. */
    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    /**
     * Kafka event type that produced this notification.
     * Values: ENROLLMENT_CONFIRMED, ENROLLMENT_DROPPED, ATTENDANCE_ALERT,
     * ASSIGNMENT_SUBMITTED, ASSIGNMENT_GRADED, QUIZ_SUBMITTED,
     * SEMESTER_CLOSED, SEMESTER_REOPENED
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** e.g. ASSIGNMENT, QUIZ, ENROLLMENT, SEMESTER — polymorphic reference. */
    @Column(name = "reference_type", length = 30)
    private String referenceType;

    /** ID of the referenced domain object. */
    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
