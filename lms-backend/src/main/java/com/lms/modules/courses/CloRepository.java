package com.lms.modules.courses;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CloRepository extends JpaRepository<Clo, UUID> {

    List<Clo> findAllByCourse_IdOrderByOrderIndex(UUID courseId);

    boolean existsByCourse_IdAndCode(UUID courseId, String code);

    boolean existsByCourse_IdAndOrderIndex(UUID courseId, int orderIndex);

    /**
     * Per-CLO coverage counts for one course: how much material teaches it, how
     * many assessments measure it, and how many PLOs it rolls up into.
     *
     * <p>Native because the assessment mapping tables belong to another module —
     * the same approach the rest of the codebase uses for cross-module reads.
     */
    @Query(value = """
            SELECT c.id::text  AS cloId,
                   c.code      AS cloCode,
                   c.title     AS cloTitle,
                   (SELECT COUNT(*) FROM material_clo_mappings m WHERE m.clo_id = c.id)
                               AS materialCount,
                   (SELECT COUNT(*) FROM assignment_clo_mappings a WHERE a.clo_id = c.id)
                   + (SELECT COUNT(*) FROM quiz_clo_mappings q WHERE q.clo_id = c.id)
                               AS assessmentCount,
                   (SELECT COUNT(*) FROM clo_plo_mappings p WHERE p.clo_id = c.id)
                               AS ploMappingCount
            FROM   clos c
            WHERE  c.course_id = :courseId
            ORDER  BY c.order_index
            """, nativeQuery = true)
    List<CloCoverageView> findCoverageByCourseId(@Param("courseId") UUID courseId);
}
