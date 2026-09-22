package com.lms.modules.exams.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Value
@Builder
public class CloMappingResponse {
    UUID cloId;
    String cloCode;
    String cloTitle;
    BigDecimal weight;
}
