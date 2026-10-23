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

    /** MCQ | MSQ | SHORT_ANSWER | LONG_ANSWER */
    @Column(nullable = false, length = 20)
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

    /** REMEMBER … CREATE; null when not classified. */
    @Column(name = "bloom_level", length = 12)
    private String bloomLevel;

    /** EASY | MEDIUM | HARD; null when not set. */
    @Column(length = 6)
    private String difficulty;

    /** Written-answer questions: what a full-marks answer says. Hidden from students. */
    @Column(name = "model_answer", columnDefinition = "TEXT")
    private String modelAnswer;

    /**
     * Written-answer marking scheme: [{"criterion":"...","marks":2,"keywords":["..."]}].
     * Hidden from students.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> rubric = List.of();

    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated = false;

    /** Answered in free text and marked by the teacher, not the auto-grader. */
    public boolean isWrittenAnswer() {
        return isWrittenType(type);
    }

    public static boolean isWrittenType(String type) {
        return "SHORT_ANSWER".equals(type) || "LONG_ANSWER".equals(type);
    }

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
