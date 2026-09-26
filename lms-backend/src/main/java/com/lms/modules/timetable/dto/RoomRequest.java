package com.lms.modules.timetable.dto;

import com.lms.modules.timetable.SessionKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create or edit a room. {@code active} defaults to true when left out. */
public record RoomRequest(
        @NotBlank @Size(max = 60) String name,
        @Size(max = 80) String building,
        @NotNull @Min(1) @Max(2000) Integer capacity,
        @NotNull SessionKind kind,
        Boolean active
) {}
