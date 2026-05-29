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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "quiz_questions")
@Getter
@Setter
@NoArgsConstructor
public class QuizQuestion extends BaseEntity {

    /** FK → quizzes.id (same module) — plain UUID. */
    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    /** MCQ | MSQ */
    @Column(nullable = false, length = 5)
    private String type = "MCQ";

    /**
     * JSONB array of option objects: [{"id":"a","text":"Option A"}, ...]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> options = List.of();

    /**
     * JSONB array of correct option ids: ["a"] for MCQ, ["a","c"] for MSQ.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correct_answer", columnDefinition = "jsonb", nullable = false)
    private List<String> correctAnswer = List.of();

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal marks;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
