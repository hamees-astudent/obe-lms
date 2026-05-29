package com.lms.modules.courses;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseAssistantRepository extends JpaRepository<CourseAssistant, CourseAssistantId> {

    List<CourseAssistant> findAllByPsc_Id(UUID pscId);

    boolean existsByIdPscIdAndIdUserId(UUID pscId, UUID userId);

    void deleteByIdPscIdAndIdUserId(UUID pscId, UUID userId);
}
