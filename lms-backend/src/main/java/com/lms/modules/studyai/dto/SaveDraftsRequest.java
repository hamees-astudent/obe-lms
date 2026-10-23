package com.lms.modules.studyai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Where to put the chosen drafts: an existing quiz or paper, or a new one.
 */
public record SaveDraftsRequest(
        @NotNull @Pattern(regexp = "QUIZ|PAPER") String target,
        UUID quizId,
        UUID paperId,
        @Valid NewQuiz newQuiz,
        @Valid NewPaper newPaper,
        @NotEmpty List<UUID> draftIds
) {
    public record NewQuiz(@NotBlank @Size(max = 255) String title, String description,
                          @Positive Integer durationMinutes) {}

    public record NewPaper(@NotBlank @Size(max = 255) String title, String instructions,
                           @Positive Integer durationMinutes, @DecimalMin("1") BigDecimal maxTotalMarks) {}
}
