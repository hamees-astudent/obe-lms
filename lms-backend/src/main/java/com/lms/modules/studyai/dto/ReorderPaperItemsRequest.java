package com.lms.modules.studyai.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Item ids in their new order; sections keep their own order when printed. */
public record ReorderPaperItemsRequest(@NotEmpty List<UUID> itemIds) {}
