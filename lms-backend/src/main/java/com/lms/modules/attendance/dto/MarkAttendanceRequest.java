package com.lms.modules.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MarkAttendanceRequest(

        @NotBlank
        @Pattern(regexp = "PRESENT|ABSENT|LATE|EXCUSED")
        String status,

        String remarks
) {}
