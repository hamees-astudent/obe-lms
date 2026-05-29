package com.lms.modules.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizCloMappingId implements Serializable {

    @Column(name = "quiz_id")
    private UUID quizId;

    /** FK → clos.id (courses module) — plain UUID, cross-module reference. */
    @Column(name = "clo_id")
    private UUID cloId;
}
