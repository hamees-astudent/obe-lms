package com.lms.modules.transcript.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CloAttainmentDetail {
    String cloCode;
    String cloTitle;
    Double attainmentPercentage;
}
