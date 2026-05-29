package com.lms.infrastructure.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lms.shared.CacheNames;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis and Spring Cache configuration.
 *
 * <ul>
 *   <li>All values are serialized as JSON (with embedded {@code @class} type metadata)
 *       so that complex objects — including those with {@link java.time.LocalDateTime}
 *       fields — survive Redis round-trips correctly.</li>
 *   <li>Redis keys are namespaced under {@code lms::<cacheName>::} to avoid
 *       collisions on a shared Redis instance.</li>
 *   <li>Per-cache TTLs are defined here; {@link CacheNames} holds the name constants.</li>
 * </ul>
 *
 * <p>The {@link RedisConnectionFactory} (Lettuce) is auto-configured by Spring Boot
 * from {@code spring.data.redis.*} properties — no manual bean required.
 */
@Configuration
public class RedisConfig {

    // ── RedisTemplate ────────────────────────────────────────────────────────

    /**
     * General-purpose {@link RedisTemplate} for direct Redis operations
     * (e.g., pub/sub, manual key management outside the cache abstraction).
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(buildRedisObjectMapper());

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    // ── CacheManager ─────────────────────────────────────────────────────────

    /**
     * Custom {@link CacheManager} backed by Redis.
     *
     * <p>Defines per-cache TTLs. All caches use:
     * <ul>
     *   <li>String key serializer</li>
     *   <li>JSON value serializer with type metadata</li>
     *   <li>{@code lms::<cacheName>::} key prefix</li>
     *   <li>Null values are never cached</li>
     * </ul>
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(buildRedisObjectMapper());

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))                          // default TTL
                .computePrefixWith(name -> "lms::" + name + "::")       // key namespace
                .serializeKeysWith(serializePair(new StringRedisSerializer()))
                .serializeValuesWith(serializePair(valueSerializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(Map.of(
                        CacheNames.USERS,             base.entryTtl(Duration.ofMinutes(30)),
                        CacheNames.COURSES,           base.entryTtl(Duration.ofHours(2)),
                        CacheNames.PROGRAMS,          base.entryTtl(Duration.ofHours(4)),
                        CacheNames.GRADING_SCALES,    base.entryTtl(Duration.ofHours(4)),
                        CacheNames.COURSE_MATERIALS,  base.entryTtl(Duration.ofHours(1)),
                        CacheNames.ENROLLMENT,        base.entryTtl(Duration.ofMinutes(30)),
                        CacheNames.ATTENDANCE_SUMMARY,base.entryTtl(Duration.ofMinutes(15))
                ))
                .build();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Builds a dedicated {@link ObjectMapper} for Redis serialization.
     *
     * <ul>
     *   <li>{@link JavaTimeModule} — serialises {@code LocalDateTime} as ISO-8601 strings.</li>
     *   <li>Default typing — embeds {@code @class} metadata so deserialisation works
     *       without knowing the concrete type at the call site.</li>
     * </ul>
     *
     * <p>This is intentionally separate from the Spring MVC {@code ObjectMapper}
     * to avoid polluting REST responses with {@code @class} fields.
     */
    private ObjectMapper buildRedisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return mapper;
    }

    private static <T> RedisSerializationContext.SerializationPair<T> serializePair(
            org.springframework.data.redis.serializer.RedisSerializer<T> serializer) {
        return RedisSerializationContext.SerializationPair.fromSerializer(serializer);
    }
}
