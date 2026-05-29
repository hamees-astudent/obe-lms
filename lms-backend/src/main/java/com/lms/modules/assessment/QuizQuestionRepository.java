package com.lms.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findAllByQuizIdOrderByOrderIndexAsc(UUID quizId);

    void deleteAllByQuizId(UUID quizId);
}
