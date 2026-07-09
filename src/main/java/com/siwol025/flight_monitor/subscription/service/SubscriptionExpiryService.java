package com.siwol025.flight_monitor.subscription.service;

import com.siwol025.flight_monitor.global.config.CacheConfig;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionExpiryService {

    private final SubscriptionRepository subscriptionRepository;
    private final CacheManager cacheManager;

    @Transactional
    public void expireSubscriptions() {
        int updated = subscriptionRepository.bulkExpireSubscriptions(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED);
        if (updated > 0) {
            Cache cache = cacheManager.getCache(CacheConfig.MONITORING_LIST_CACHE);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
