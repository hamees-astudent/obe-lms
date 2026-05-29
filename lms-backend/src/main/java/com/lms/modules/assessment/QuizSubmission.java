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

    /** Timestamp when the student first opened the quiz. */
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
