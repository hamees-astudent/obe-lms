package com.lms.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmDto(

        @NotBlank
        String token,

        @NotBlank
        @Size(min = 8, max = 128)
        String newPassword
) {}
