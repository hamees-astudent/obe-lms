package com.lms.modules.courses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateCourseMaterialRequest(

        @NotBlank @Size(max = 255)
        String title,

        String description,

        Map<String, Object> content,

        boolean visible,

        int orderIndex
) {}
