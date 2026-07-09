package com.siwol025.flight_monitor.subscription.service;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionExpiryScheduler {

    private static final String LOCK_KEY = "expiry:subscription:lock";
    private static final long WAIT_TIME = 0L;
    private static final long LEASE_TIME = 50L;

    private final SubscriptionExpiryService subscriptionExpiryService;
    private final RedissonClient redissonClient;

    @Scheduled(cron = "${subscription.expiry.cron:0 0 * * * *}")
    public void expireSubscriptions() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            if (lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS)) {
                subscriptionExpiryService.expireSubscriptions();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
