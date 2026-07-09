package com.siwol025.flight_monitor.subscription.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.siwol025.flight_monitor.global.config.CacheConfig;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import java.util.List;
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
    void expireSubscriptions_만료대상있음_EXPIRED로_변경됨() {
        Subscription sub1 = mock(Subscription.class);
        Subscription sub2 = mock(Subscription.class);

        given(subscriptionRepository.findExpirableSubscriptions(SubscriptionStatus.ACTIVE))
                .willReturn(List.of(sub1, sub2));

        subscriptionExpiryService.expireSubscriptions();

        then(sub1).should().updateStatus(SubscriptionStatus.EXPIRED);
        then(sub2).should().updateStatus(SubscriptionStatus.EXPIRED);
    }

    @Test
    void expireSubscriptions_만료대상없음_아무처리없음() {
        given(subscriptionRepository.findExpirableSubscriptions(SubscriptionStatus.ACTIVE))
                .willReturn(List.of());

        subscriptionExpiryService.expireSubscriptions();

        then(subscriptionRepository).should().findExpirableSubscriptions(SubscriptionStatus.ACTIVE);
        then(cacheManager).shouldHaveNoInteractions();
    }

    @Test
    void expireSubscriptions_캐시_무효화됨() {
        Subscription sub = mock(Subscription.class);
        Cache mockCache = mock(Cache.class);

        given(subscriptionRepository.findExpirableSubscriptions(SubscriptionStatus.ACTIVE))
                .willReturn(List.of(sub));
        given(cacheManager.getCache(CacheConfig.MONITORING_LIST_CACHE))
                .willReturn(mockCache);

        subscriptionExpiryService.expireSubscriptions();

        then(mockCache).should().clear();
    }
}
