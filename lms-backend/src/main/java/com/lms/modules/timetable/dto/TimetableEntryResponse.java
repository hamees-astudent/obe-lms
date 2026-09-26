package com.lms.modules.timetable.dto;

import com.lms.modules.timetable.SessionKind;

import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

/** One weekly class meeting, with everything a timetable cell shows about it. */
public record TimetableEntryResponse(
        UUID id,
        UUID pscId,
        String courseCode,
        String courseName,
        UUID programId,
        String programName,
        String semesterName,
        UUID teacherId,
        String teacherName,
        UUID roomId,
        String roomName,
        SessionKind kind,
        int dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        int students,
        /** Cohorts with members in this class (admin view only; empty otherwise). */
        Set<UUID> cohortIds
) {}
