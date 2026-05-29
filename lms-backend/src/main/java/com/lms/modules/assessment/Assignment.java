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
@Table(name = "assignments")
@Getter
@Setter
@NoArgsConstructor
public class Assignment extends BaseEntity {

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

    /** FILE | TEXT | BOTH — what students may submit. */
    @Column(name = "submission_type", nullable = false, length = 10)
    private String submissionType = "FILE";

    @Column(name = "total_marks", nullable = false, precision = 7, scale = 2)
    private BigDecimal totalMarks;

    @Column(name = "due_date", nullable = false)
    private LocalDateTime dueDate;

    @Column(name = "allow_late_submission", nullable = false)
    private boolean allowLateSubmission = false;

    /** Percentage deducted for late submissions; null = no penalty applied. */
    @Column(name = "late_penalty_percent", precision = 5, scale = 2)
    private BigDecimal latePenaltyPercent;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
