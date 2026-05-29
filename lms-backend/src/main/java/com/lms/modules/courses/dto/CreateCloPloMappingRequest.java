package com.lms.modules.courses.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCloPloMappingRequest(

        @NotNull
        UUID ploId,

        /** Optional contribution weight 0–100 %. Null = qualitative mapping only. */
        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax("100")
        BigDecimal weight
) {}
