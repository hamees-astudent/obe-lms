package com.lms.modules.courses.dto;

import java.util.UUID;

/** An offering the current user teaches or assists, with where it sits. */
public record TeachingOfferingResponse(
        UUID id,
        UUID semesterId,
        String semesterName,
        String programName,
        UUID courseId,
        String courseCode,
        String courseName,
        int creditHours,
        UUID teacherId
) {}
