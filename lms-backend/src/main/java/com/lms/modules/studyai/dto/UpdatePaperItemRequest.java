package com.lms.modules.studyai.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdatePaperItemRequest(@NotNull @DecimalMin("0.5") BigDecimal marks) {}
