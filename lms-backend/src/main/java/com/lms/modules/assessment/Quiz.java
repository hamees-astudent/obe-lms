package com.lms.modules.assessment;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor
public class Quiz extends BaseEntity {

    /** FK → program_semester_courses.id (courses module) — plain UUID. */
    @Column(name = "psc_id", nullable = false)
    private UUID pscId;

    /** FK → users.id (users module) — plain UUID. */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Null = unlimited time. */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /** Cached sum of all question marks; updated when questions are added/removed. */
    @Column(name = "total_marks", nullable = false, precision = 7, scale = 2)
    private BigDecimal totalMarks = BigDecimal.ZERO;

    /** Null = not yet scheduled / always open. */
    @Column(name = "available_from")
    private LocalDateTime availableFrom;

    @Column(name = "available_until")
    private LocalDateTime availableUntil;

    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions = false;

    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions = false;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
