package com.lms.modules.exams;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExamRepository extends JpaRepository<Exam, UUID> {

    List<Exam> findAllByPscIdOrderByExamDateDesc(UUID pscId);

    List<Exam> findAllByPscIdInAndExamDate(List<UUID> pscIds, LocalDate examDate);

    boolean existsByPscIdAndExamTypeAndExamDate(UUID pscId, String examType, LocalDate examDate);
}
