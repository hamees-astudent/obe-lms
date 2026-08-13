package com.lms.modules.enrollment.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record EnrollmentResponse(
        UUID id,
        UUID pscId,
        UUID studentId,
        String studentName,
        /** Institutional roll number; null on endpoints that do not resolve it. */
        String studentNumber,
        String courseRole,
        String status,
        LocalDateTime enrolledAt,
        LocalDateTime droppedAt,
        LocalDateTime createdAt
) {}
