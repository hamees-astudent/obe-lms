package com.lms.modules.enrollment.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record EnrollmentResponse(
        UUID id,
        UUID pscId,
        UUID studentId,
        String status,
        LocalDateTime enrolledAt,
        LocalDateTime droppedAt,
        LocalDateTime createdAt
) {}
