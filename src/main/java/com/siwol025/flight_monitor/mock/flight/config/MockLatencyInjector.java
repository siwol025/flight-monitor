package com.siwol025.flight_monitor.mock.flight.config;

import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MockLatencyInjector {

    private final MockLatencyProperties properties;

    public long computeDelayMs() {
        if (!properties.isEnabled()) {
            return 0L;
        }

        long jitterMs = properties.getJitterMs();
        long jitter = jitterMs > 0 ? ThreadLocalRandom.current().nextLong(0, jitterMs + 1) : 0L;

        return properties.getBaseMs() + jitter;
    }
}
