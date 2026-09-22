package com.lms.modules.exams;

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
public class ExamQuestionCloMappingId implements Serializable {

    @Column(name = "question_id")
    private UUID questionId;

    /** FK → clos.id (courses module) — plain UUID, cross-module reference. */
    @Column(name = "clo_id")
    private UUID cloId;
}
