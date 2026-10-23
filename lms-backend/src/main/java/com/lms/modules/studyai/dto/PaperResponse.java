package com.lms.modules.studyai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PaperResponse(
        UUID id,
        UUID pscId,
        String title,
        String instructions,
        Integer durationMinutes,
        BigDecimal maxTotalMarks,
        BigDecimal totalMarks,
        /** DRAFT | FINAL (printed) */
        String status,
        int questionCount,
        int maxQuestions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** Present when one paper is fetched. */
        List<PaperItemResponse> items,
        List<PrintSetResponse> printSets
) {}
