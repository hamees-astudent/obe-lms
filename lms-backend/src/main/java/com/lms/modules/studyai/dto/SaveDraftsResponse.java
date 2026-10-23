package com.lms.modules.studyai.dto;

import java.util.UUID;

public record SaveDraftsResponse(
        /** QUIZ | PAPER */
        String target,
        UUID targetId,
        int saved
) {}
