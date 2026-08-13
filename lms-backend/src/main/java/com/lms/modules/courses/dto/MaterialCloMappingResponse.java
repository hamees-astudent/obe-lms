package com.lms.modules.courses.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MaterialCloMappingResponse(
        UUID materialId,
        UUID cloId,
        String cloCode,
        String cloTitle,
        BigDecimal weight,
        LocalDateTime createdAt
) {}
