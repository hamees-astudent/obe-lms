package com.lms.modules.exams;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** The mark a student earned on one question of one exam. */
@Entity
@Table(name = "exam_question_marks")
@Getter
@Setter
@NoArgsConstructor
public class ExamQuestionMark extends BaseEntity {

    /** FK → exam_results.id (same module) — plain UUID. */
    @Column(name = "result_id", nullable = false)
    private UUID resultId;

    /** FK → exam_questions.id (same module) — plain UUID. */
    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    /**
     * Null means the question was not attempted, which is deliberately distinct
     * from {@code 0} (attempted, earned nothing). Attainment treats both as zero
     * marks, but the distinction is visible to a teacher reviewing the copy.
     */
    @Column(name = "marks_obtained", precision = 6, scale = 2)
    private BigDecimal marksObtained;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
