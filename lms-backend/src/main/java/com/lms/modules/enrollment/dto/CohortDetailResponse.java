package com.lms.modules.enrollment.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CohortDetailResponse(
        UUID id,
        String name,
        String description,
        List<CohortMemberResponse> members,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
