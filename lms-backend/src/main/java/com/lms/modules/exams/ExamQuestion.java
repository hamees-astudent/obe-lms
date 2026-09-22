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

/**
 * One row of an exam's marks table.
 *
 * <p>{@link #questionNo} is free text rather than an integer because real papers
 * number their parts ("2a", "2b", "Q4(i)"), and this label is the join key
 * between what the extractor reads off a copy and the exam's known structure.
 */
@Entity
@Table(name = "exam_questions")
@Getter
@Setter
@NoArgsConstructor
public class ExamQuestion extends BaseEntity {

    /** FK → exams.id (same module) — plain UUID. */
    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    /** The label as printed on the copy, e.g. {@code 1}, {@code 3a}. */
    @Column(name = "question_no", nullable = false, length = 10)
    private String questionNo;

    @Column(name = "max_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxMarks;

    /** 1-based display ordering within the exam. */
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
