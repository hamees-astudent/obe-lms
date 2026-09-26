package com.lms.modules.timetable.dto;

import java.util.List;

/**
 * A timetable for the current term.
 *
 * @param offerings        offerings the view covers
 * @param sessionsRequired weekly meetings those offerings need
 * @param sessionsPlaced   weekly meetings the timetable has
 */
public record TimetableResponse(
        TimetableGridResponse grid,
        List<TimetableEntryResponse> entries,
        List<UnscheduledOfferingResponse> unscheduled,
        int offerings,
        int sessionsRequired,
        int sessionsPlaced
) {}
