package com.lms.modules.studyai.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateChatSessionRequest(@NotNull UUID pscId) {}
