package com.lms.modules.studyai.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** A mock question; the answer fields are filled in only once the attempt is graded. */
public record MockQuestionResponse(
        String id,
        String type,
        String questionText,
        List<Map<String, Object>> options,
        BigDecimal marks,
        String bloomLevel,
        List<String> correctAnswer,
        String modelAnswer,
        String explanation,
        List<CitationResponse> sources
) {}
