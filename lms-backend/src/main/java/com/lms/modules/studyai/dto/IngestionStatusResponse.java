package com.lms.modules.studyai.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** Whether a document material has been read and indexed for the study assistant. */
public record IngestionStatusResponse(
        UUID materialId,
        String title,
        /** PENDING | PROCESSING | READY | FAILED | UNSUPPORTED */
        String status,
        String error,
        Integer pageCount,
        Integer chunkCount,
        int topicCount,
        boolean visible,
        LocalDateTime finishedAt
) {}
