package com.lms.modules.assessment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Replaces the CLOs a question is mapped to. */
public record QuestionCloMappingsRequest(@NotNull List<UUID> cloIds) {}
