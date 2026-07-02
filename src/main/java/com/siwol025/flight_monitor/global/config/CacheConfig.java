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
    // TTL: 5분
    private static final long DEFAULT_TTL_MILLIS = 300_000L;

    @Bean
    public MonitoringListCache monitoringListCache() {
        return new MonitoringListCache(MONITORING_LIST_CACHE, DEFAULT_TTL_MILLIS);
    }

    @Bean
    public CacheManager cacheManager(MonitoringListCache monitoringListCache) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(monitoringListCache));
        return cacheManager;
    }
}
