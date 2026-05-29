package com.lms.modules.programs.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Full program detail — includes the ordered PLO list. */
public record ProgramDetailResponse(
        UUID id,
        String name,
        String code,
        String description,
        int durationYears,
        String status,
        LocalDateTime createdAt,
        List<PloResponse> plos
) {}
