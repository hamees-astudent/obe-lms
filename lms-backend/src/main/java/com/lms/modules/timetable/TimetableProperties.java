package com.lms.modules.timetable;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.List;

/**
 * The weekly teaching grid the timetable is built on.
 *
 * <p>Classes start only at the start of a slot. A lab takes two slots back to
 * back, so it can only begin where one slot ends exactly as the next begins —
 * never across the lunch break.
 */
@Component
@ConfigurationProperties(prefix = "app.timetable")
@Data
public class TimetableProperties {

    /** Teaching days, Monday first. */
    private List<DayOfWeek> days = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    /** Periods of each teaching day as {@code HH:mm-HH:mm}, in order. */
    private List<String> slots = List.of(
            "08:30-10:00", "10:00-11:30", "11:30-13:00", "14:00-15:30", "15:30-17:00");

    /** Number of back-to-back slots one lab session takes. */
    private int labSlots = 2;

    /** Attempts with differently ordered classes; the best timetable wins. */
    private int attempts = 40;
}
