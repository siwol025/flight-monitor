package com.siwol025.flight_monitor.subscription.service;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
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
            // 의도적 미해제: LEASE_TIME 자동 만료로 단일 주기 단일 실행 보장 (CLAUDE.md 참고)
            if (lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS)) {
                subscriptionExpiryService.expireSubscriptions();
            }
        } catch (InterruptedException e) {
            log.warn("만료 스케줄러 인터럽트 발생", e);
            Thread.currentThread().interrupt();
        }
    }
}
