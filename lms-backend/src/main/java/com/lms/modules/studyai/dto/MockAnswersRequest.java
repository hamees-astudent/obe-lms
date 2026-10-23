package com.lms.modules.studyai.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/** Question id → chosen option ids, or a one-element list holding a written answer. */
public record MockAnswersRequest(@NotNull Map<String, List<String>> answers) {}
