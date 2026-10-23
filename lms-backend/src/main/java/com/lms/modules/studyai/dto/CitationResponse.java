package com.lms.modules.studyai.dto;

import java.util.UUID;

/** A passage of course material an answer or question is grounded in. */
public record CitationResponse(
        /** The marker used in the text, e.g. "S2". */
        String ref,
        UUID chunkId,
        UUID materialId,
        String materialTitle,
        String locationType,
        int locationStart,
        int locationEnd,
        /** "Slide 12", "Pages 4–5". */
        String locationLabel,
        String heading
) {}
