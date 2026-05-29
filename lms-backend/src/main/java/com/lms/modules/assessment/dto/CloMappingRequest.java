package com.lms.modules.assessment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CloMappingRequest {

    @NotNull
    private UUID cloId;

    @DecimalMin("0.01")
    @DecimalMax("100")
    private BigDecimal weight;
}
