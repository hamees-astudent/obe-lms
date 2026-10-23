package com.lms.modules.studyai.dto;

import java.time.LocalDateTime;

public record PrintSetResponse(
        String setCode,
        boolean shuffledQuestions,
        boolean shuffledOptions,
        LocalDateTime printedAt
) {}
