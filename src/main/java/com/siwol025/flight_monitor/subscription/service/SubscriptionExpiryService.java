package com.siwol025.flight_monitor.subscription.service;

import com.siwol025.flight_monitor.global.config.CacheConfig;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionExpiryService {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    @CacheEvict(value = CacheConfig.MONITORING_LIST_CACHE, allEntries = true)
    public void expireSubscriptions() {
        int updated = subscriptionRepository.bulkExpireSubscriptions(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED);
        log.info("만료 처리 완료: {}건", updated);
    }
}
