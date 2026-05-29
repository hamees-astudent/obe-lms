package com.lms.modules.assessment.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class CloMappingResponse {
    UUID assessmentId;
    UUID cloId;
    BigDecimal weight;
    LocalDateTime createdAt;
}
