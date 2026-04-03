package com.siwol025.flight_monitor.global.config;

import com.siwol025.flight_monitor.monitor.utils.MonitoringListCache;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableCaching
@Configuration
public class CacheConfig {

    public static final String MONITORING_LIST_CACHE = "MONITORING_LIST_CACHE";
    // TTL: 5분 (밀리초 단위 계산)
    private static final long DEFAULT_TTL_MILLIS = 300_000L;

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        cacheManager.setCaches(List.of(new MonitoringListCache(MONITORING_LIST_CACHE, DEFAULT_TTL_MILLIS)));

        return cacheManager;
    }
}
