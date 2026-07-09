package com.siwol025.flight_monitor.subscription.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.siwol025.flight_monitor.global.config.CacheConfig;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpiryServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private CacheManager cacheManager;

    @InjectMocks
    private SubscriptionExpiryService subscriptionExpiryService;

    @Test
    void expireSubscriptions_만료대상있음_bulkExpireSubscriptions_호출됨() {
        given(subscriptionRepository.bulkExpireSubscriptions(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED))
                .willReturn(1);

        subscriptionExpiryService.expireSubscriptions();

        then(subscriptionRepository).should().bulkExpireSubscriptions(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED);
    }

    @Test
    void expireSubscriptions_만료대상없음_캐시무효화_호출없음() {
        given(subscriptionRepository.bulkExpireSubscriptions(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED))
                .willReturn(0);

        subscriptionExpiryService.expireSubscriptions();

        then(cacheManager).should(never()).getCache(CacheConfig.MONITORING_LIST_CACHE);
    }

    @Test
    void expireSubscriptions_캐시_무효화됨() {
        Cache mockCache = mock(Cache.class);

        given(subscriptionRepository.bulkExpireSubscriptions(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED))
                .willReturn(1);
        given(cacheManager.getCache(CacheConfig.MONITORING_LIST_CACHE))
                .willReturn(mockCache);

        subscriptionExpiryService.expireSubscriptions();

        then(cacheManager).should().getCache(CacheConfig.MONITORING_LIST_CACHE);
        then(mockCache).should().clear();
    }
}
