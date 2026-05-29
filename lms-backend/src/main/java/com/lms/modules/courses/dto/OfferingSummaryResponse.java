package com.lms.modules.courses.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record OfferingSummaryResponse(
        UUID id,
        UUID semesterId,
        UUID courseId,
        String courseCode,
        String courseName,
        int creditHours,
        UUID teacherId,
        int maxCapacity,
        LocalDateTime createdAt
) {}
