package com.lms.modules.attendance;

/**
 * Projection for the per-student per-course attendance summary native query.
 * Counts: attended (PRESENT or LATE), total (all non-EXCUSED records).
 */
interface AttendanceSummaryView {
    long getAttended();
    long getTotal();
}
