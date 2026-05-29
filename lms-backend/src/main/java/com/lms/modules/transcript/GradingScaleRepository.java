package com.lms.modules.transcript;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradingScaleRepository extends JpaRepository<GradingScale, UUID> {

    List<GradingScale> findAllByProgramIdOrderByCreatedAtDesc(UUID programId);

    List<GradingScale> findAllByProgramIdIsNullOrderByCreatedAtDesc();

    /**
     * Resolves the active grading scale for a program using the priority order:
     * program-specific default first, global default second.
     */
    @Query(value = """
            SELECT *
            FROM   grading_scales
            WHERE  (program_id = :programId OR program_id IS NULL)
            AND    is_default = TRUE
            ORDER  BY program_id NULLS LAST
            LIMIT  1
            """, nativeQuery = true)
    Optional<GradingScale> findActiveScaleForProgram(@Param("programId") UUID programId);

    Optional<GradingScale> findByProgramIdAndIsDefaultTrue(UUID programId);

    Optional<GradingScale> findByProgramIdIsNullAndIsDefaultTrue();
}
