package com.lms.modules.auth.dto;

import com.lms.shared.Role;

import java.util.UUID;

public record LoginResponse(

        String accessToken,
        String refreshToken,

        /** Access token lifetime in milliseconds — lets the client schedule proactive refresh. */
        long expiresIn,

        UserInfo user
) {
    public record UserInfo(UUID id, String name, String email, Role role) {}
}
