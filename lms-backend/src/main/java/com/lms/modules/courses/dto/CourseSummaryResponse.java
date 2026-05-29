package com.lms.modules.courses.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CourseSummaryResponse(
        UUID id,
        String code,
        String name,
        String description,
        int creditHours,
        String status,
        LocalDateTime createdAt
) {}
