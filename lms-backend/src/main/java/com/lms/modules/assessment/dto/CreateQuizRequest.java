package com.lms.modules.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CreateQuizRequest {

    @NotNull
    private UUID pscId;

    @NotBlank
    @Size(max = 255)
    private String title;

    private String description;

    /** Null = unlimited time. */
    @Positive
    private Integer durationMinutes;

    private LocalDateTime availableFrom;

    private LocalDateTime availableUntil;

    private boolean shuffleQuestions = false;

    private boolean shuffleOptions = false;
}
