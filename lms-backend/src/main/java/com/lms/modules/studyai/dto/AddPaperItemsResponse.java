package com.lms.modules.studyai.dto;

public record AddPaperItemsResponse(
        int added,
        /** Left out to stay within the paper's limits (fitToLimit only). */
        int skipped,
        PaperResponse paper
) {}
