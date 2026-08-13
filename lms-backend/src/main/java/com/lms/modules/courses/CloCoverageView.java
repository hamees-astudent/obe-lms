package com.lms.modules.courses;

/**
 * Projection for {@link CloRepository#findCoverageByCourseId}. Counts come back
 * from native SQL, so the CLO id is a string.
 */
interface CloCoverageView {
    String getCloId();
    String getCloCode();
    String getCloTitle();
    long getMaterialCount();
    long getAssessmentCount();
    long getPloMappingCount();
}
