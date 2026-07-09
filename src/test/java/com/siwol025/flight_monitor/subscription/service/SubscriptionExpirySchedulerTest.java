package com.siwol025.flight_monitor.subscription.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpirySchedulerTest {

    @Mock
    private SubscriptionExpiryService subscriptionExpiryService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock mockLock;

    @InjectMocks
    private SubscriptionExpiryScheduler subscriptionExpiryScheduler;

    @Test
    void expireScheduler_락_획득성공_expireSubscriptions_호출됨() throws Exception {
        given(redissonClient.getLock("expiry:subscription:lock")).willReturn(mockLock);
        given(mockLock.tryLock(0L, 50L, TimeUnit.SECONDS)).willReturn(true);

        subscriptionExpiryScheduler.expireSubscriptions();

        then(subscriptionExpiryService).should().expireSubscriptions();
    }

    @Test
    void expireScheduler_락_획득실패_expireSubscriptions_호출안됨() throws Exception {
        given(redissonClient.getLock("expiry:subscription:lock")).willReturn(mockLock);
        given(mockLock.tryLock(0L, 50L, TimeUnit.SECONDS)).willReturn(false);

        subscriptionExpiryScheduler.expireSubscriptions();

        then(subscriptionExpiryService).shouldHaveNoInteractions();
    }
}
