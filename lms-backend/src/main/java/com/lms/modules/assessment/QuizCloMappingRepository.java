package com.lms.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuizCloMappingRepository extends JpaRepository<QuizCloMapping, QuizCloMappingId> {

    List<QuizCloMapping> findAllById_QuizId(UUID quizId);

    List<QuizCloMapping> findAllById_CloId(UUID cloId);

    @Modifying
    @Query("DELETE FROM QuizCloMapping m WHERE m.id.quizId = :quizId AND m.id.cloId = :cloId")
    void deleteByQuizIdAndCloId(@Param("quizId") UUID quizId, @Param("cloId") UUID cloId);

    boolean existsById_QuizIdAndId_CloId(UUID quizId, UUID cloId);
}
