package com.lms.modules.studyai.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record PaperItemResponse(
        UUID id,
        /** CHOICE | SHORT | LONG — printed as sections A, B, C. */
        String section,
        int orderIndex,
        BigDecimal marks,
        /** The question as it will print: type, questionText, options, correctAnswer, modelAnswer, rubric, … */
        Map<String, Object> question
) {}
