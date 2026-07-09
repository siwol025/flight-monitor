package com.siwol025.flight_monitor.subscription.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionStatusTest {

    @Test
    void subscriptionStatus_EXPIRED_isActive_false() {
        assertThat(SubscriptionStatus.EXPIRED.isActive()).isFalse();
    }

    @Test
    void subscriptionStatus_ACTIVE_isActive_true() {
        assertThat(SubscriptionStatus.ACTIVE.isActive()).isTrue();
    }
}
