package com.lms.infrastructure.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.QueryTimeoutException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The cache must never be load-bearing. An unreachable Redis previously turned
 * every attendance mark into a 500, because the eviction that follows the write
 * propagated its connection failure to the caller.
 */
@ExtendWith(MockitoExtension.class)
class CacheEvictorTest {

    @Mock private CacheManager cacheManager;
    @Mock private Cache        cache;

    @Test
    @DisplayName("evicts the entry when the cache is healthy")
    void evictsNormally() {
        when(cacheManager.getCache("attendance-summary")).thenReturn(cache);

        new CacheEvictor(cacheManager).evict("attendance-summary", "key");

        verify(cache).evict("key");
    }

    @Test
    @DisplayName("an unreachable cache server does not fail the caller")
    void swallowsConnectionFailure() {
        when(cacheManager.getCache(any())).thenReturn(cache);
        doThrow(new QueryTimeoutException("Redis connection refused"))
                .when(cache).evict(any());

        assertThatCode(() -> new CacheEvictor(cacheManager).evict("attendance-summary", "key"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unknown cache name is ignored rather than thrown")
    void toleratesMissingCache() {
        when(cacheManager.getCache(any())).thenReturn(null);

        assertThatCode(() -> new CacheEvictor(cacheManager).evict("no-such-cache", "key"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a cache manager that itself fails does not fail the caller")
    void swallowsManagerFailure() {
        when(cacheManager.getCache(any()))
                .thenThrow(new IllegalStateException("cache manager unavailable"));

        assertThatCode(() -> new CacheEvictor(cacheManager).evict("attendance-summary", "key"))
                .doesNotThrowAnyException();
    }
}
