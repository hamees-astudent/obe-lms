package com.lms.modules.courses;

/**
 * Projection for {@link ProgramSemesterCourseRepository#findTeachingOfferings}.
 *
 * <p>Public on purpose: Spring Data returns projections as JDK dynamic proxies,
 * and the proxy is defined in a {@code jdk.proxy*} module that cannot reach a
 * package-private type — every query returning it would fail at runtime with
 * {@code IllegalAccessError}.
 */
public interface TeachingOfferingView {
    String getPscId();
    String getSemesterName();
    String getProgramName();
}
