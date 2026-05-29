package com.lms.modules.courses.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CloResponse(
        UUID id,
        UUID courseId,
        String code,
        String title,
        String description,
        int orderIndex,
        LocalDateTime createdAt
) {}
