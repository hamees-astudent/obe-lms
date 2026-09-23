package com.lms;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deadlines are zone-less and compared against the JVM clock, so the JVM zone
 * must be the institution's, not whatever the host happens to run in.
 */
class LmsApplicationTimeZoneTest {

    private TimeZone original;

    @BeforeEach
    void remember() {
        original = TimeZone.getDefault();
    }

    @AfterEach
    void restore() {
        TimeZone.setDefault(original);
    }

    @Test
    @DisplayName("with nothing configured, the JVM runs in the institution's zone")
    void defaultsToInstitutionZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        LmsApplication.pinTimeZone(null);
        assertThat(TimeZone.getDefault().getID()).isEqualTo(LmsApplication.DEFAULT_TIME_ZONE);
    }

    @Test
    @DisplayName("APP_TIME_ZONE overrides the default")
    void honoursConfiguredZone() {
        LmsApplication.pinTimeZone("Europe/London");
        assertThat(TimeZone.getDefault().getID()).isEqualTo("Europe/London");
    }

    @Test
    @DisplayName("a misspelt zone stops startup instead of silently falling back to UTC")
    void rejectsUnknownZone() {
        assertThatThrownBy(() -> LmsApplication.pinTimeZone("Asia/Karachy"))
                .isInstanceOf(DateTimeException.class);
    }
}
