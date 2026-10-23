package com.lms.modules.studyai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GenerationJobResponse(
        UUID id,
        UUID pscId,
        /** PENDING | RUNNING | READY | FAILED */
        String status,
        /** LLM | TEMPLATE | HYBRID */
        String mode,
        String error,
        Map<String, Object> config,
        List<String> notes,
        BigDecimal totalMarks,
        int draftCount,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        /** Present when one job is fetched. */
        List<QuestionDraftResponse> drafts
) {}
