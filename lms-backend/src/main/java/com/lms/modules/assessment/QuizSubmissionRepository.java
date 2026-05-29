package com.lms.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, UUID> {

    List<QuizSubmission> findAllByQuizId(UUID quizId);

    List<QuizSubmission> findAllByStudentId(UUID studentId);

    Optional<QuizSubmission> findByQuizIdAndStudentId(UUID quizId, UUID studentId);
}
