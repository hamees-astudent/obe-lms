package com.lms.modules.timetable.dto;

import java.time.LocalTime;
import java.util.List;

/** The teaching week the timetable is drawn on. */
public record TimetableGridResponse(List<Day> days, List<Slot> slots, int labSlots) {

    /** {@code dayOfWeek} is ISO: 1 = Monday. */
    public record Day(int dayOfWeek, String name) {}

    public record Slot(int index, LocalTime start, LocalTime end) {}
}
