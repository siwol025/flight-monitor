package com.siwol025.flight_monitor.monitor.utils;

import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class DynamicTtlCalculator {

    public Duration calculateCooldownTtl(LocalDateTime departureTime, LocalDateTime currentTime) {
        Duration timeUntilDeparture = Duration.between(currentTime, departureTime);
        if (timeUntilDeparture.isNegative() || timeUntilDeparture.isZero()) {
            return Duration.ZERO;
        }

        long daysLeft = timeUntilDeparture.toDays();

        if (daysLeft > 30) {
            return Duration.ofHours(6);
        }
        if (daysLeft > 7) {
            return Duration.ofHours(1);
        }
        return Duration.ofMillis(10);
    }
}
