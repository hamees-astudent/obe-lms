package com.lms.modules.studyai.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ChatSessionResponse(
        UUID id,
        UUID pscId,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** Present when one session is fetched; null in lists. */
        List<ChatMessageResponse> messages
) {}
