package com.lms.modules.courses.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CloPloMappingResponse(
        UUID cloId,
        UUID ploId,
        BigDecimal weight,
        LocalDateTime createdAt
) {}
