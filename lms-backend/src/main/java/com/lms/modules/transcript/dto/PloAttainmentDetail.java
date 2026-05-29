package com.lms.modules.transcript.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PloAttainmentDetail {
    String ploCode;
    String ploTitle;
    Double attainmentPercentage;
}
