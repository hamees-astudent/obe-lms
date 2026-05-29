package com.lms.modules.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeStatusRequest(
        @NotBlank
        @Pattern(regexp = "ACTIVE|INACTIVE|SUSPENDED")
        String status
) {}
