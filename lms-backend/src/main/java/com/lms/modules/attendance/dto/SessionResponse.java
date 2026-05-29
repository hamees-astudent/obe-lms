package com.lms.modules.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID pscId,
        UUID createdBy,
        LocalDate sessionDate,
        String topic,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        boolean open,
        LocalDateTime createdAt
) {}
