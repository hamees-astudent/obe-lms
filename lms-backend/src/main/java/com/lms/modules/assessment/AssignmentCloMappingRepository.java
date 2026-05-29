package com.lms.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AssignmentCloMappingRepository extends JpaRepository<AssignmentCloMapping, AssignmentCloMappingId> {

    List<AssignmentCloMapping> findAllById_AssignmentId(UUID assignmentId);

    List<AssignmentCloMapping> findAllById_CloId(UUID cloId);

    @Modifying
    @Query("DELETE FROM AssignmentCloMapping m WHERE m.id.assignmentId = :assignmentId AND m.id.cloId = :cloId")
    void deleteByAssignmentIdAndCloId(@Param("assignmentId") UUID assignmentId, @Param("cloId") UUID cloId);

    boolean existsById_AssignmentIdAndId_CloId(UUID assignmentId, UUID cloId);
}
