package com.lms.modules.exams.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class ExamQuestionResponse {
    UUID id;
    String questionNo;
    BigDecimal maxMarks;
    int orderIndex;
    List<CloMappingResponse> cloMappings;
}
