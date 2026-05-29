package com.lms.modules.attendance.dto;

import java.util.UUID;

public record AttendanceSummaryResponse(
        UUID pscId,
        UUID studentId,
        long attended,
        long total,
        double percentage
) {}
