package com.lms.modules.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateQuizRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    private String description;

    @Positive
    private Integer durationMinutes;

    private LocalDateTime availableFrom;

    private LocalDateTime availableUntil;

    private boolean shuffleQuestions = false;

    private boolean shuffleOptions = false;
}
