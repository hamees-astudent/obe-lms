package com.lms.modules.courses.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record CreateOfferingRequest(

        @NotNull
        UUID semesterId,

        @NotNull
        UUID courseId,

        @NotNull
        UUID teacherId,

        @Min(0)
        int maxCapacity
) {}
