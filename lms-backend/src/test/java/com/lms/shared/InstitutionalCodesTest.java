package com.lms.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the code grammar. The old rule ({@code [A-Z0-9_]+}) rejected every
 * hyphenated DCS-UBIT code, so no real course could be created.
 */
class InstitutionalCodesTest {

    private static final Pattern CODE = Pattern.compile(InstitutionalCodes.CODE_PATTERN);

    @ParameterizedTest
    @DisplayName("accepts the department's real code formats")
    @ValueSource(strings = {
            "BSCS-501",   // hyphenated program-scoped course
            "CS-363",     // hyphenated department course
            "CS-363L",    // lab section suffix
            "CS101",      // legacy unseparated form
            "MATH_101",   // underscore separator
            "BSCS",       // program code
            "EE-201-A",   // multiple segments
    })
    void acceptsValidCodes(String code) {
        assertThat(CODE.matcher(InstitutionalCodes.normalise(code)).matches())
                .as("%s should be a valid code", code)
                .isTrue();
    }

    @ParameterizedTest
    @DisplayName("rejects malformed codes")
    @ValueSource(strings = {
            "-CS101",     // leading separator
            "CS101-",     // trailing separator
            "CS--101",    // doubled separator
            "CS 101",     // whitespace
            "CS@101",     // punctuation
            "",           // empty
    })
    void rejectsInvalidCodes(String code) {
        assertThat(CODE.matcher(InstitutionalCodes.normalise(code)).matches())
                .as("%s should be rejected", code)
                .isFalse();
    }

    @ParameterizedTest
    @DisplayName("normalises case and surrounding whitespace")
    @ValueSource(strings = { "cs-363", " CS-363 ", "Cs-363" })
    void normalisesToUpperCase(String input) {
        assertThat(InstitutionalCodes.normalise(input)).isEqualTo("CS-363");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("normalise is null-safe")
    void normaliseHandlesNull() {
        assertThat(InstitutionalCodes.normalise(null)).isNull();
    }
}
