package com.lms.modules.timetable.dto;

import com.lms.modules.timetable.SessionKind;

import java.util.List;
import java.util.UUID;

/**
 * An offering the timetable is short of meetings for.
 *
 * @param missing the weekly meetings not yet placed
 * @param reason  why the generator could not place them; null when it has
 *                simply not been run since the offering was created
 */
public record UnscheduledOfferingResponse(
        UUID pscId,
        String courseCode,
        String courseName,
        String programName,
        String teacherName,
        int students,
        List<SessionKind> missing,
        String reason
) {}
