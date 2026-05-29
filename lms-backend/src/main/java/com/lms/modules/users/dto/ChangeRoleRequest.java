package com.lms.modules.users.dto;

import com.lms.shared.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull Role role) {}
