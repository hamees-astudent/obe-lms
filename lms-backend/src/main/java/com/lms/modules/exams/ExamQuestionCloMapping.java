package com.lms.modules.exams;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Links one exam question to a CLO, so exam marks feed OBE attainment.
 *
 * <p>Mapping at the question level rather than the whole exam (as
 * {@code AssignmentCloMapping} and {@code QuizCloMapping} do) is possible only
 * because a marks table is captured per question. It makes attainment
 * attributable: a low CLO score points at the questions that caused it.
 */
@Entity
@Table(name = "exam_question_clo_mappings")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class ExamQuestionCloMapping {

    @EmbeddedId
    private ExamQuestionCloMappingId id = new ExamQuestionCloMappingId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("questionId")
    @JoinColumn(name = "question_id")
    private ExamQuestion question;

    /** Relative contribution weight; null = equal weighting. */
    @Column(precision = 5, scale = 2)
    private BigDecimal weight;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ExamQuestionCloMapping of(ExamQuestion question, UUID cloId, BigDecimal weight) {
        ExamQuestionCloMapping m = new ExamQuestionCloMapping();
        m.getId().setQuestionId(question.getId());
        m.getId().setCloId(cloId);
        m.setQuestion(question);
        m.setWeight(weight);
        return m;
    }
}
