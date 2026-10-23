package com.lms.modules.studyai.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        /** USER | ASSISTANT */
        String role,
        String content,
        List<CitationResponse> citations,
        boolean refused,
        /** LLM | EXTRACTIVE; null for the student's own messages. */
        String mode,
        boolean unverified,
        LocalDateTime createdAt
) {}
