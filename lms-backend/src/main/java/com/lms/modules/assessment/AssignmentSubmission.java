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
@Table(name = "assignment_submissions")
@Getter
@Setter
@NoArgsConstructor
public class AssignmentSubmission extends BaseEntity {

    /** FK → assignments.id (same module) — plain UUID. */
    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    /** FK → users.id (users module) — plain UUID. */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** DRAFT | SUBMITTED | LATE | GRADED */
    @Column(nullable = false, length = 10)
    private String status = "DRAFT";

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "file_key", length = 512)
    private String fileKey;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "marks_obtained", precision = 7, scale = 2)
    private BigDecimal marksObtained;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    /** FK → users.id — plain UUID; null while ungraded. */
    @Column(name = "graded_by")
    private UUID gradedBy;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
