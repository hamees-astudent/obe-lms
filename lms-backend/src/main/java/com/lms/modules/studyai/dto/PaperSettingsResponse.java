package com.lms.modules.studyai.dto;

import com.lms.modules.studyai.MarksPolicy;

import java.math.BigDecimal;
import java.util.UUID;

public record PaperSettingsResponse(
        UUID pscId,
        BigDecimal maxTotalMarks,
        int maxQuestions,
        int maxDurationMinutes,
        BigDecimal mockMaxTotalMarks,
        MarksPolicy marksTable
) {}
