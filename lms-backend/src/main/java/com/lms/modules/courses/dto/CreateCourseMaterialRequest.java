package com.lms.modules.courses.dto;

import jakarta.validation.constraints.*;

import java.util.Map;

public record CreateCourseMaterialRequest(

        @NotBlank
        @Pattern(regexp = "DOCUMENT|URL|ASSIGNMENT|QUIZ|ANNOUNCEMENT|VIDEO_LINK")
        String type,

        @NotBlank @Size(max = 255)
        String title,

        String description,

        /** Type-specific JSONB payload — see CourseMaterial Javadoc for expected shapes. */
        Map<String, Object> content,

        boolean visible,

        int orderIndex
) {}
