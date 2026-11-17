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
        String heading,
        /**
         * The cited passage itself (trimmed), so the reader can check what the
         * answer or question rests on without opening the file. Null on
         * citations stored before excerpts were kept.
         */
        String excerpt
) {
    /** Longest excerpt carried with a citation, in characters. */
    public static final int EXCERPT_CHARS = 1200;

    /** The passage cut to {@link #EXCERPT_CHARS} at a word boundary. */
    public static String excerptOf(String content) {
        if (content == null || content.isBlank()) return null;
        String t = content.strip();
        if (t.length() <= EXCERPT_CHARS) return t;
        String cut = t.substring(0, EXCERPT_CHARS);
        int space = cut.lastIndexOf(' ');
        return (space > EXCERPT_CHARS * 0.8 ? cut.substring(0, space) : cut) + " …";
    }
}
