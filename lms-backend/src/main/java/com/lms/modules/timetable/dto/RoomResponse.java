package com.lms.modules.timetable.dto;

import com.lms.modules.timetable.SessionKind;

import java.util.UUID;

/** A room, with how many weekly class meetings the timetable has put in it. */
public record RoomResponse(
        UUID id,
        String name,
        String building,
        int capacity,
        SessionKind kind,
        boolean active,
        long weeklyClasses
) {}
