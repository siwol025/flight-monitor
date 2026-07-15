package com.siwol025.flight_monitor.mock.flight.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mock.latency")
public class MockLatencyProperties {

    private boolean enabled = false;
    private long baseMs = 0;
    private long jitterMs = 0;
}
