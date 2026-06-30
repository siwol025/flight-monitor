package com.siwol025.flight_monitor.monitor.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CacheStrategyTest {

    @Test
    void FlightPriceCacheManager가_CacheStrategy를_구현해야한다() {
        boolean implementsCacheStrategy = CacheStrategy.class.isAssignableFrom(FlightPriceCacheManager.class);
        assertThat(implementsCacheStrategy).isTrue();
    }
}
