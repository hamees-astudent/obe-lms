package com.lms.modules.programs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SemesterResponse(
        UUID id,
        UUID programId,
        String programName,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        LocalDateTime closedAt,
        UUID closedById,
        LocalDateTime createdAt
) {}
