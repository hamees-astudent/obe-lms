package com.lms.modules.courses.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateOfferingRequest(

        @NotNull
        UUID teacherId,

        @Min(0)
        int maxCapacity
) {}
