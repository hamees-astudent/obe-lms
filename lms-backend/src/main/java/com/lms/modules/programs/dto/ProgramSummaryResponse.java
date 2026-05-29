package com.lms.modules.programs.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** Lightweight projection used in paginated list responses. */
public record ProgramSummaryResponse(
        UUID id,
        String name,
        String code,
        String description,
        int durationYears,
        String status,
        LocalDateTime createdAt
) {}
