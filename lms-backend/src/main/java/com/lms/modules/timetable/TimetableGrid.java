package com.lms.modules.timetable;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The parsed teaching grid: which days are taught and which periods each day has.
 *
 * <p>Built once from {@link TimetableProperties} and checked then, so a typo in
 * the configured slots stops startup instead of producing a timetable with
 * overlapping periods.
 */
public record TimetableGrid(List<DayOfWeek> days, List<Slot> slots, int labSlots) {

    /** One period of the teaching day. */
    public record Slot(int index, LocalTime start, LocalTime end) {}

    public TimetableGrid {
        days = List.copyOf(days);
        slots = List.copyOf(slots);
        if (days.isEmpty()) {
            throw new IllegalArgumentException("app.timetable.days must name at least one day");
        }
        if (days.stream().distinct().count() != days.size()) {
            throw new IllegalArgumentException("app.timetable.days lists a day twice");
        }
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("app.timetable.slots must list at least one period");
        }
        for (int i = 1; i < slots.size(); i++) {
            if (slots.get(i).start().isBefore(slots.get(i - 1).end())) {
                throw new IllegalArgumentException("app.timetable.slots must be in order and must not overlap: "
                        + slots.get(i - 1) + " and " + slots.get(i));
            }
        }
        if (labSlots < 1 || labSlots > slots.size()) {
            throw new IllegalArgumentException("app.timetable.lab-slots must be between 1 and the number of slots");
        }
    }

    public static TimetableGrid from(TimetableProperties props) {
        List<Slot> slots = new ArrayList<>();
        for (String spec : props.getSlots()) {
            String[] parts = spec.split("-");
            try {
                if (parts.length != 2) throw new DateTimeParseException("expected HH:mm-HH:mm", spec, 0);
                LocalTime start = LocalTime.parse(parts[0].trim());
                LocalTime end = LocalTime.parse(parts[1].trim());
                if (!end.isAfter(start)) {
                    throw new IllegalArgumentException("Timetable slot ends before it starts: " + spec);
                }
                slots.add(new Slot(slots.size(), start, end));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Timetable slot is not HH:mm-HH:mm: \"" + spec + "\"", e);
            }
        }
        return new TimetableGrid(props.getDays(), slots, props.getLabSlots());
    }

    /** Slots a session of this kind occupies. */
    public int length(SessionKind kind) {
        return kind == SessionKind.LAB ? labSlots : 1;
    }

    /**
     * Whether a session of {@code length} slots can start at {@code first}:
     * it must fit in the day, and each slot must begin exactly when the one
     * before it ends, so a lab never straddles the lunch break.
     */
    public boolean canStart(int first, int length) {
        if (first < 0 || first + length > slots.size()) return false;
        for (int i = first + 1; i < first + length; i++) {
            if (!slots.get(i).start().equals(slots.get(i - 1).end())) return false;
        }
        return true;
    }

    public Optional<Slot> slotStartingAt(LocalTime time) {
        return slots.stream().filter(s -> s.start().equals(time)).findFirst();
    }

    public LocalTime endOf(int first, int length) {
        return slots.get(first + length - 1).end();
    }
}
