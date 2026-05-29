package com.lms.modules.programs.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PloResponse(
        UUID id,
        UUID programId,
        String code,
        String title,
        String description,
        int orderIndex,
        LocalDateTime createdAt
) {}
