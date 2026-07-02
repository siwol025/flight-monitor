package com.siwol025.flight_monitor.monitor.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

class MonitoringListCacheTest {

    @Test
    void evictExpiredEntries_만료된항목_두맵_모두_제거됨() throws InterruptedException {
        // given
        MonitoringListCache cache = new MonitoringListCache("test-cache", 1);
        cache.put("k1", "v1");

        // when
        Thread.sleep(5);
        cache.evictExpiredEntries();

        // then: 부모 store에서도 제거되었는지 getNativeCache()로 직접 확인
        ConcurrentMap<?, ?> store = (ConcurrentMap<?, ?>) cache.getNativeCache();
        assertThat(store.containsKey("k1")).isFalse();
        assertThat(cache.get("k1")).isNull();
    }

    @Test
    void evictExpiredEntries_유효한항목_보존됨() {
        // given
        MonitoringListCache cache = new MonitoringListCache("test-cache", 60_000);
        cache.put("k2", "v2");

        // when
        cache.evictExpiredEntries();

        // then
        assertThat(cache.get("k2")).isNotNull();
        assertThat(cache.get("k2").get()).isEqualTo("v2");
    }
}
