package com.lms.modules.courses.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Full course detail including its ordered CLO list. */
public record CourseDetailResponse(
        UUID id,
        String code,
        String name,
        String description,
        int creditHours,
        String status,
        LocalDateTime createdAt,
        List<CloResponse> clos
) {}
