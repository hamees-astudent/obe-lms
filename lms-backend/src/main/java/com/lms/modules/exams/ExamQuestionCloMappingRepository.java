package com.lms.modules.exams;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ExamQuestionCloMappingRepository
        extends JpaRepository<ExamQuestionCloMapping, ExamQuestionCloMappingId> {

    List<ExamQuestionCloMapping> findAllById_QuestionId(UUID questionId);

    List<ExamQuestionCloMapping> findAllById_CloId(UUID cloId);

    @Query("SELECT m FROM ExamQuestionCloMapping m WHERE m.id.questionId IN :questionIds")
    List<ExamQuestionCloMapping> findAllByQuestionIds(@Param("questionIds") List<UUID> questionIds);

    @Modifying
    @Query("DELETE FROM ExamQuestionCloMapping m WHERE m.id.questionId = :questionId AND m.id.cloId = :cloId")
    void deleteByQuestionIdAndCloId(@Param("questionId") UUID questionId, @Param("cloId") UUID cloId);

    boolean existsById_QuestionIdAndId_CloId(UUID questionId, UUID cloId);
}
