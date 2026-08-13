package com.lms.modules.courses;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaterialCloMappingRepository
        extends JpaRepository<MaterialCloMapping, MaterialCloMappingId> {

    List<MaterialCloMapping> findAllByMaterial_Id(UUID materialId);

    /** All materials mapped to a given CLO — drives coverage reporting. */
    List<MaterialCloMapping> findAllByClo_Id(UUID cloId);

    boolean existsByIdMaterialIdAndIdCloId(UUID materialId, UUID cloId);
}
