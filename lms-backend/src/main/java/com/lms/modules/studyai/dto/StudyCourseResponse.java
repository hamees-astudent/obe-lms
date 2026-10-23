package com.lms.modules.studyai.dto;

import java.util.UUID;

/** A course the student can use the study assistant for. */
public record StudyCourseResponse(
        UUID pscId,
        String courseCode,
        String courseName,
        String semesterName,
        /** Visible materials indexed and searchable. */
        int readyMaterials
) {}
