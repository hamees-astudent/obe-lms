package com.lms.modules.attendance;

/**
 * Projection for the per-student per-course attendance summary native query.
 * Counts: attended (PRESENT or LATE), total (all non-EXCUSED records).
 *
 * <p>Public on purpose: Spring Data returns projections as JDK dynamic proxies,
 * and the proxy is defined in a {@code jdk.proxy*} module that cannot reach a
 * package-private type. Narrowing this back to package-private makes every
 * query that returns it fail at runtime with {@code IllegalAccessError}.
 */
public interface AttendanceSummaryView {
    long getAttended();
    long getTotal();
}
