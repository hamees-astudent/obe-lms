package com.lms.modules.programs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PloRepository extends JpaRepository<Plo, UUID> {

    List<Plo> findAllByProgramIdOrderByOrderIndex(UUID programId);

    boolean existsByProgramIdAndCode(UUID programId, String code);

    boolean existsByProgramIdAndOrderIndex(UUID programId, int orderIndex);
}
