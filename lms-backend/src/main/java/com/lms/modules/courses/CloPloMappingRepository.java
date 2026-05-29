package com.lms.modules.courses;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CloPloMappingRepository extends JpaRepository<CloPloMapping, CloPloMappingId> {

    List<CloPloMapping> findAllByClo_Id(UUID cloId);

    /** All CLO→PLO mappings that point to a given PLO (used at transcript generation). */
    List<CloPloMapping> findAllById_PloId(UUID ploId);

    boolean existsByIdCloIdAndIdPloId(UUID cloId, UUID ploId);
}
