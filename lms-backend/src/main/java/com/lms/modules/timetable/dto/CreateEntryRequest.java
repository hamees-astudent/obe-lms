package com.lms.modules.timetable.dto;

import com.lms.modules.timetable.SessionKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.UUID;

/** Place one weekly meeting of an offering by hand. */
public record CreateEntryRequest(
        @NotNull UUID pscId,
        @NotNull SessionKind kind,
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull UUID roomId
) {}
