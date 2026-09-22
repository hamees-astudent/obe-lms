package com.lms.modules.exams;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExamScanRepository extends JpaRepository<ExamScan, UUID> {

    List<ExamScan> findAllByExamIdOrderByCreatedAtDesc(UUID examId);

    List<ExamScan> findAllByExamIdAndStatusOrderByCreatedAtDesc(UUID examId, String status);

    List<ExamScan> findTop50ByUploadedByOrderByCreatedAtDesc(UUID uploadedBy);
}
