package com.lms.modules.studyai.dto;

import java.math.BigDecimal;
import java.util.Map;

/** How many marks a requested mix comes to, checked against the limits. */
public record GenerationEstimateResponse(
        BigDecimal estimatedMarks,
        BigDecimal maxTotalMarks,
        /** The offering's ceiling, which maxTotalMarks may not exceed. */
        BigDecimal offeringMaxTotalMarks,
        int questionCount,
        int maxQuestions,
        Map<String, BigDecimal> marksPerQuestion,
        boolean withinLimit,
        /** Why it is not within the limit; null when it is. */
        String problem
) {}
