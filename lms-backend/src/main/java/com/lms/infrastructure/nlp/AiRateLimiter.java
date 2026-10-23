package com.lms.infrastructure.nlp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Per-user caps on the NLP features, so one student scripting the chat cannot
 * occupy the CPU the model runs on for everyone else.
 *
 * <p>A fixed-window counter in Redis. When Redis is unreachable the limit is
 * not enforced rather than blocking every request: like the cache, Redis is
 * not a source of truth here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRateLimiter {

    public enum Window {
        HOUR(Duration.ofHours(1), DateTimeFormatter.ofPattern("yyyyMMddHH")),
        DAY(Duration.ofDays(1), DateTimeFormatter.ofPattern("yyyyMMdd"));

        final Duration ttl;
        final DateTimeFormatter bucket;

        Window(Duration ttl, DateTimeFormatter bucket) {
            this.ttl = ttl;
            this.bucket = bucket;
        }
    }

    private final StringRedisTemplate redis;

    /** Counts one use of {@code feature}; refuses with 429 past {@code limit}. */
    public void check(UUID userId, String feature, int limit, Window window) {
        if (limit <= 0) return;
        String key = "nlp:rl:" + feature + ":" + userId + ":" + LocalDateTime.now().format(window.bucket);
        Long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, window.ttl);
        } catch (Exception e) {
            log.warn("Rate limiter unavailable ({}); not enforcing {} limit", e.getMessage(), feature);
            return;
        }
        if (count != null && count > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You have reached the limit of " + limit + " " + feature.replace('-', ' ')
                    + " requests per " + window.name().toLowerCase() + ". Try again later.");
        }
    }
}
