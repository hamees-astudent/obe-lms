package com.lms.modules.exams;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, UUID> {

    List<ExamQuestion> findAllByExamIdOrderByOrderIndexAsc(UUID examId);

    void deleteAllByExamId(UUID examId);

    boolean existsByExamIdAndQuestionNo(UUID examId, String questionNo);
}
