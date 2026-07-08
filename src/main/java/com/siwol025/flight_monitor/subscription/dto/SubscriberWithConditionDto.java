package com.siwol025.flight_monitor.subscription.dto;

import java.math.BigDecimal;

public record SubscriberWithConditionDto(
        String email,
        BigDecimal targetPrice,
        BigDecimal dropThresholdPercent
) {
}
