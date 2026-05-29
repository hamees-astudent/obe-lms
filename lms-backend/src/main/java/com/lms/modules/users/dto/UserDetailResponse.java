package com.lms.modules.users.dto;

import com.lms.shared.Role;

import java.time.LocalDateTime;
import java.util.UUID;

/** Full user detail — includes role-specific profile data when available. */
public record UserDetailResponse(
        UUID id,
        String name,
        String email,
        Role role,
        String status,
        LocalDateTime createdAt,
        StudentProfileResponse studentProfile,
        TeacherProfileResponse teacherProfile
) {}
