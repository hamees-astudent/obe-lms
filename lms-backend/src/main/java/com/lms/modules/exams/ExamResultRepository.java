package com.lms.modules.exams;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamResultRepository extends JpaRepository<ExamResult, UUID> {

    List<ExamResult> findAllByExamId(UUID examId);

    Optional<ExamResult> findByExamIdAndStudentId(UUID examId, UUID studentId);

    List<ExamResult> findAllByStudentId(UUID studentId);

    long countByExamId(UUID examId);
}
