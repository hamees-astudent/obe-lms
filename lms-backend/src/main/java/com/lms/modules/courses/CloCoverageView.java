package com.lms.modules.courses;

/**
 * Projection for {@link CloRepository#findCoverageByCourseId}. Counts come back
 * from native SQL, so the CLO id is a string.
 *
 * <p>Public on purpose: Spring Data returns projections as JDK dynamic proxies,
 * and the proxy is defined in a {@code jdk.proxy*} module that cannot reach a
 * package-private type. Narrowing this back to package-private makes every
 * query that returns it fail at runtime with {@code IllegalAccessError}.
 */
public interface CloCoverageView {
    String getCloId();
    String getCloCode();
    String getCloTitle();
    long getMaterialCount();
    long getAssessmentCount();
    long getPloMappingCount();
}
