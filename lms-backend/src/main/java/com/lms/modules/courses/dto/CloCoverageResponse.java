package com.lms.modules.courses.dto;

import java.util.UUID;

/**
 * One row of the OBE coverage report: how well a single CLO is served.
 *
 * <p>A CLO with no material is taught by nothing; a CLO with no assessment is
 * never measured; a CLO with no PLO mapping contributes to no program outcome.
 * Each is a gap worth showing before an accreditation review finds it.
 */
public record CloCoverageResponse(
        UUID cloId,
        String cloCode,
        String cloTitle,
        long materialCount,
        long assessmentCount,
        long ploMappingCount
) {
    public boolean isFullyCovered() {
        return materialCount > 0 && assessmentCount > 0 && ploMappingCount > 0;
    }
}
