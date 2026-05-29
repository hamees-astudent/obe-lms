package com.lms.modules.users.dto;

import com.lms.shared.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(

        @NotBlank
        @Size(max = 120)
        String name,

        @NotBlank
        @Email
        @Size(max = 180)
        String email,

        /** Plain-text initial password — will be BCrypt-hashed before storage. */
        @NotBlank
        @Size(min = 8, max = 128)
        String password,

        @NotNull
        Role role
) {}
