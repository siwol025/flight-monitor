package com.siwol025.flight_monitor.subscription.service;

import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpirySchedulerTest {

    @Mock
    private SubscriptionExpiryService subscriptionExpiryService;

    @InjectMocks
    private SubscriptionExpiryScheduler subscriptionExpiryScheduler;

    @Test
    void expireScheduler_스케줄링_expireSubscriptions_호출됨() {
        subscriptionExpiryScheduler.expireSubscriptions();

        then(subscriptionExpiryService).should().expireSubscriptions();
    }
}
