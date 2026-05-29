package com.lms.modules.courses.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record CourseMaterialResponse(
        UUID id,
        UUID pscId,
        UUID uploadedBy,
        String type,
        String title,
        String description,
        Map<String, Object> content,
        boolean visible,
        int orderIndex,
        LocalDateTime createdAt
) {}
