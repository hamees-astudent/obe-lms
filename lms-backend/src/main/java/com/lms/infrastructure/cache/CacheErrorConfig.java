package com.lms.infrastructure.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the cache non-load-bearing.
 *
 * <p>Spring's default {@code SimpleCacheErrorHandler} rethrows, so an
 * unreachable Redis turns every cached read and every eviction into a 500 — a
 * teacher marking attendance gets "Internal Server Error" even though the row
 * was written and committed. Redis here is a latency optimisation, not a source
 * of truth: when it is unavailable the correct behaviour is to fall through to
 * the database and carry on.
 *
 * <p>Failures are logged at WARN rather than swallowed silently, so a genuinely
 * broken cache is still visible in the logs and to alerting.
 */
@Slf4j
@Configuration
public class CacheErrorConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                // Treated as a cache miss — the caller recomputes from the database.
                log.warn("Cache read failed [{}::{}] — falling back to source: {}",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key,
                                            Object value) {
                log.warn("Cache write failed [{}::{}] — value not cached: {}",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                // The entry may now be stale. That is worse than a miss but far
                // better than failing the write that triggered the eviction.
                log.warn("Cache evict failed [{}::{}] — entry may be stale until TTL: {}",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("Cache clear failed [{}]: {}", cache.getName(), ex.getMessage());
            }
        };
    }
}
