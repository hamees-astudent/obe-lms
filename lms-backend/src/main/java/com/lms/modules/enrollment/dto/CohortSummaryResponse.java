package com.lms.modules.enrollment.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CohortSummaryResponse(
        UUID id,
        String name,
        String description,
        long memberCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
