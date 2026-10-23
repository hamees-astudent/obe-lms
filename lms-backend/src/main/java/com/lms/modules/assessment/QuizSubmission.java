package com.lms.modules.assessment;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "quiz_submissions")
@Getter
@Setter
@NoArgsConstructor
public class QuizSubmission extends BaseEntity {

    /** FK → quizzes.id (same module) — plain UUID. */
    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    /** FK → users.id (users module) — plain UUID. */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /**
     * JSONB map of questionId (string) → selected option-id list.
     * Example: {"<uuid>": ["a"], "<uuid>": ["b","c"]}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, List<String>> answers = new HashMap<>();

    /** Null while the submission is in progress. */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** Computed by auto-grader on submit; null until then. */
    @Column(precision = 7, scale = 2)
    private BigDecimal score;

    @Column(name = "is_auto_graded", nullable = false)
    private boolean autoGraded = false;

    /** IN_PROGRESS | PENDING_REVIEW | GRADED. */
    @Column(name = "grading_status", nullable = false, length = 16)
    private String gradingStatus = STATUS_IN_PROGRESS;

    /**
     * Per-question marks and feedback:
     * {"<question-uuid>": {"score": 2.5, "feedback": "...", "gradedBy": "AUTO|TEACHER"}}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_scores", columnDefinition = "jsonb", nullable = false)
    private Map<String, Map<String, Object>> questionScores = new HashMap<>();

    /** Teacher who finalised the written answers; null for auto-graded attempts. */
    @Column(name = "graded_by")
    private UUID gradedBy;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;

    public static final String STATUS_IN_PROGRESS    = "IN_PROGRESS";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_GRADED         = "GRADED";

    /** Timestamp when the student first opened the quiz. */
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
