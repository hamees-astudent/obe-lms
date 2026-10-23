package com.lms.modules.studyai.dto;

import java.math.BigDecimal;
import java.util.List;

/** A suggested mark for one written answer; the teacher decides the final one. */
public record GradeSuggestionResponse(
        String questionId,
        BigDecimal score,
        BigDecimal maxMarks,
        String feedback,
        /** LLM | CLASSICAL | NONE */
        String method,
        BigDecimal classicalScore,
        BigDecimal modelScore,
        /** The two methods disagree by a lot: read this answer carefully. */
        boolean disagreement,
        List<String> covered,
        List<String> missing
) {}
