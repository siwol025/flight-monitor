package com.siwol025.flight_monitor.subscription.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubscriptionStatus {
    ACTIVE(true), INACTIVE(false);

    private final boolean isActive;
}
