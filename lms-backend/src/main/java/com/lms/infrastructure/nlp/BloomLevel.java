package com.lms.infrastructure.nlp;

import java.util.Locale;
import java.util.Optional;

/** Revised Bloom's taxonomy, lowest cognitive level first. */
public enum BloomLevel {
    REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE;

    public static Optional<BloomLevel> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (v.equals("ANALYSE")) v = "ANALYZE";
        try {
            return Optional.of(valueOf(v));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** 1 (Remember) … 6 (Create). */
    public int rank() {
        return ordinal() + 1;
    }
}
