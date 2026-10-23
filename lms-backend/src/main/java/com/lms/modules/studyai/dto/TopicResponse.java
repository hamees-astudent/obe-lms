package com.lms.modules.studyai.dto;

import java.util.UUID;

/** A topic a material covers, with the pages/slides/sections it spans. */
public record TopicResponse(
        UUID id,
        UUID materialId,
        String materialTitle,
        String name,
        String summary,
        /** PAGE | SLIDE | SECTION */
        String locationType,
        int locationStart,
        int locationEnd
) {}
