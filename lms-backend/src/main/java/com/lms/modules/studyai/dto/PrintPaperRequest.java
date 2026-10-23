package com.lms.modules.studyai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** How many differently-ordered sets to print, and what to shuffle. */
public record PrintPaperRequest(
        @Min(1) @Max(6) int sets,
        boolean shuffleQuestions,
        boolean shuffleOptions
) {}
