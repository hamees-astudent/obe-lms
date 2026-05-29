package com.lms.modules.users.dto;

import com.lms.shared.Role;

import java.time.LocalDateTime;
import java.util.UUID;

/** Lightweight projection used in paginated list responses. */
public record UserSummaryResponse(
        UUID id,
        String name,
        String email,
        Role role,
        String status,
        LocalDateTime createdAt
) {}
