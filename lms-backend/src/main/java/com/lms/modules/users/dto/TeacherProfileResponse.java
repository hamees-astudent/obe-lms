package com.lms.modules.users.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TeacherProfileResponse(
        UUID userId,
        String employeeNumber,
        String department,
        String designation,
        String phone,
        String profilePictureKey,
        LocalDate joiningDate,
        String bio,
        LocalDateTime createdAt
) {}
