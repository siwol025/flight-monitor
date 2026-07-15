package com.siwol025.flight_monitor.mock.flight.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MockLatencyInjectorTest {

    @Test
    void 지연주입_비활성화시_지연0() {
        MockLatencyProperties properties = new MockLatencyProperties();
        properties.setEnabled(false);
        properties.setBaseMs(50);
        properties.setJitterMs(100);
        MockLatencyInjector injector = new MockLatencyInjector(properties);

        long delay = injector.computeDelayMs();

        assertThat(delay).isZero();
    }

    @Test
    void 지연주입_활성화시_base이상_base더하기jitter이하_지연계산() {
        MockLatencyProperties properties = new MockLatencyProperties();
        properties.setEnabled(true);
        properties.setBaseMs(50);
        properties.setJitterMs(100);
        MockLatencyInjector injector = new MockLatencyInjector(properties);

        long delay = injector.computeDelayMs();

        assertThat(delay).isBetween(50L, 150L);
    }

    @Test
    void 지연주입_jitter0시_정확히base() {
        MockLatencyProperties properties = new MockLatencyProperties();
        properties.setEnabled(true);
        properties.setBaseMs(50);
        properties.setJitterMs(0);
        MockLatencyInjector injector = new MockLatencyInjector(properties);

        long delay = injector.computeDelayMs();

        assertThat(delay).isEqualTo(50L);
    }
}
