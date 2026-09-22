package com.lms.modules.exams;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExamQuestionMarkRepository extends JpaRepository<ExamQuestionMark, UUID> {

    List<ExamQuestionMark> findAllByResultId(UUID resultId);

    void deleteAllByResultId(UUID resultId);
}
