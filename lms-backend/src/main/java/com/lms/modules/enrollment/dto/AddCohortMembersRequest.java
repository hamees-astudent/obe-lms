package com.lms.modules.enrollment.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Students to add to a cohort, by id (picked in the UI), by roll number
 * (pasted from a class list), or both.
 */
public record AddCohortMembersRequest(
        @Size(max = 1000) List<UUID> studentIds,
        @Size(max = 1000) List<String> studentNumbers
) {
    @AssertTrue(message = "Give at least one student id or roll number")
    public boolean isNotEmpty() {
        return (studentIds != null && !studentIds.isEmpty())
                || (studentNumbers != null && studentNumbers.stream().anyMatch(n -> n != null && !n.isBlank()));
    }
}
