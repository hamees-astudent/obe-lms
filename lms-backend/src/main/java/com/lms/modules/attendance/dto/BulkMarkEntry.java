package com.lms.modules.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record BulkMarkEntry(

        @NotNull
        UUID studentId,

        @NotNull
        @Pattern(regexp = "PRESENT|ABSENT|LATE|EXCUSED")
        String status,

        String remarks
) {}
