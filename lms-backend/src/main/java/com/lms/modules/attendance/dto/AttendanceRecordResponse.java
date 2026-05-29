package com.lms.modules.attendance.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AttendanceRecordResponse(
        UUID id,
        UUID sessionId,
        UUID studentId,
        String status,
        UUID markedBy,
        String remarks,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
