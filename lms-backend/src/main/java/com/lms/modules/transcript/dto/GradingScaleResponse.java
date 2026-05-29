package com.lms.modules.transcript.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class GradingScaleResponse {
    UUID                          id;
    UUID                          programId;
    String                        name;
    boolean                       isDefault;
    List<GradingScaleEntryResponse> entries;
    LocalDateTime                 createdAt;
    LocalDateTime                 updatedAt;
}
