package com.lms.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Evicts cache entries explicitly, without letting a cache outage break the
 * business operation that triggered the eviction.
 *
 * <p>Services reach for this instead of {@code @CacheEvict} when the eviction
 * happens inside the same bean as its caller: a self-invocation never passes
 * through the caching proxy, so the annotation would be silently inert.
 *
 * <p>{@link CacheErrorConfig} cannot help here — its {@code CacheErrorHandler}
 * only sees failures raised through the cache interceptor, and a direct
 * {@link CacheManager} call bypasses it. Hence the try/catch below.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictor {

    private final CacheManager cacheManager;

    /**
     * Removes one entry. A missing cache or an unreachable cache server is
     * logged and ignored — the entry then survives until its TTL, which is a
     * far smaller problem than failing a write that already committed.
     */
    public void evict(String cacheName, Object key) {
        try {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (RuntimeException ex) {
            log.warn("Cache evict failed [{}::{}] — entry may be stale until TTL: {}",
                    cacheName, key, ex.getMessage());
        }
    }
}
