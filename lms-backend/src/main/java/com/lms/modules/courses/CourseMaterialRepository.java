package com.lms.modules.courses;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseMaterialRepository extends JpaRepository<CourseMaterial, UUID> {

    List<CourseMaterial> findAllByPsc_IdOrderByOrderIndex(UUID pscId);

    List<CourseMaterial> findAllByPsc_IdAndVisibleTrueOrderByOrderIndex(UUID pscId);

    List<CourseMaterial> findAllByPsc_IdAndTypeOrderByOrderIndex(UUID pscId, String type);
}
