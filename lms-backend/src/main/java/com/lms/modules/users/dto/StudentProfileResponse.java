package com.lms.modules.users.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record StudentProfileResponse(
        UUID userId,
        String studentNumber,
        LocalDate dateOfBirth,
        String phone,
        String address,
        String profilePictureKey,
        LocalDate enrollmentDate,
        LocalDateTime createdAt
) {}
