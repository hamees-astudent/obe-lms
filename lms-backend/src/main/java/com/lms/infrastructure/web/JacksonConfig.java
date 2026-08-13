package com.lms.infrastructure.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Web-layer Jackson customisations.
 *
 * <p>The domain models date-times as {@link LocalDateTime} (zone-free wall-clock
 * time). Browsers, however, hand out offset-carrying strings very easily —
 * {@code Date#toISOString()} always appends a {@code Z} — and the stock
 * deserializer rejects those with a 400 that reads as "the whole form is
 * broken". Rather than push that trap onto every client, the API accepts both
 * forms: an offset, when present, is converted to the configured zone and then
 * dropped.
 *
 * <p>Serialization is unchanged: responses stay zone-free.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule lenientLocalDateTimeModule() {
        SimpleModule module = new SimpleModule("lenient-local-date-time");
        module.addDeserializer(LocalDateTime.class, new LenientLocalDateTimeDeserializer());
        return module;
    }

    static class LenientLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctx)
                throws IOException {
            String text = p.getText();
            if (text == null || text.isBlank()) {
                return null;
            }
            String value = text.trim();
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException noOffsetFailed) {
                try {
                    // "2026-08-13T09:00:00.000Z" / "…+05:00" → local wall-clock time.
                    return OffsetDateTime.parse(value)
                            .atZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDateTime();
                } catch (DateTimeParseException stillFailed) {
                    throw ctx.weirdStringException(value, LocalDateTime.class,
                            "expected an ISO-8601 date-time such as 2026-08-13T09:00:00");
                }
            }
        }
    }
}
