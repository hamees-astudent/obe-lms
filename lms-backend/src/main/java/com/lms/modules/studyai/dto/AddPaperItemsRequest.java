package com.lms.modules.studyai.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Questions to add to a paper, from any mix of sources. With
 * {@code fitToLimit} the questions that would push the paper over its limits
 * are skipped; without it the whole request is refused.
 */
public record AddPaperItemsRequest(
        List<UUID> draftIds,
        /** Existing quiz questions of this offering. */
        List<UUID> quizQuestionIds,
        /** Questions written by hand, in the same shape as a paper item's question. */
        List<Map<String, Object>> questions,
        boolean fitToLimit
) {}
