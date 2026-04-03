package com.siwol025.flight_monitor.monitor.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cache.concurrent.ConcurrentMapCache;

public class MonitoringListCache extends ConcurrentMapCache {

    private final Map<Object, Long> ttlCache = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public MonitoringListCache(String name, long ttlMillis) {
        super(name);
        this.ttlMillis = ttlMillis;
    }

    @Override
    public Object lookup(final Object key) {
        final Long expiredTime = ttlCache.get(key);
        if (expiredTime == null || isCacheValid(expiredTime)) {
            return super.lookup(key);
        }

        ttlCache.remove(key);
        super.evict(key);
        return null;
    }

    @Override
    public void put(final Object key, final Object value) {
        Long expiredTime = System.currentTimeMillis() + ttlMillis;

        ttlCache.put(key, expiredTime);
        super.put(key,value);
    }

    @Override
    public void evict(Object key) {
        ttlCache.remove(key);
        super.evict(key);
    }

    @Override
    public void clear() {
        ttlCache.clear();
        super.clear();
    }

    private boolean isCacheValid(Long expiredTime) {
        return System.currentTimeMillis() < expiredTime;
    }
}
