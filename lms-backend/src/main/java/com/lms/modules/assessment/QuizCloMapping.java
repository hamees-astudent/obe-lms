package com.lms.modules.assessment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "quiz_clo_mappings")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class QuizCloMapping {

    @EmbeddedId
    private QuizCloMappingId id = new QuizCloMappingId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("quizId")
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;

    /** Relative contribution weight; null = equal weighting. */
    @Column(precision = 5, scale = 2)
    private BigDecimal weight;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static QuizCloMapping of(Quiz quiz, UUID cloId, BigDecimal weight) {
        QuizCloMapping m = new QuizCloMapping();
        m.getId().setQuizId(quiz.getId());
        m.getId().setCloId(cloId);
        m.setQuiz(quiz);
        m.setWeight(weight);
        return m;
    }
}
