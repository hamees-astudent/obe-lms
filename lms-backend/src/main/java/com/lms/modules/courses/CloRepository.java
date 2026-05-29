package com.lms.modules.courses;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CloRepository extends JpaRepository<Clo, UUID> {

    List<Clo> findAllByCourse_IdOrderByOrderIndex(UUID courseId);

    boolean existsByCourse_IdAndCode(UUID courseId, String code);

    boolean existsByCourse_IdAndOrderIndex(UUID courseId, int orderIndex);
}
