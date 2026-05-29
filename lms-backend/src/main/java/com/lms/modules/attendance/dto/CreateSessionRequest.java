package com.lms.modules.attendance.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateSessionRequest(

        @NotNull
        UUID pscId,

        @NotNull
        LocalDate sessionDate,

        String topic
) {}
