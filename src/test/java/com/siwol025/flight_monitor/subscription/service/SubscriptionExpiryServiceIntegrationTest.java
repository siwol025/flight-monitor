package com.siwol025.flight_monitor.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.siwol025.flight_monitor.global.config.CacheConfig;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig
class SubscriptionExpiryServiceIntegrationTest {

    @Configuration
    @EnableCaching
    @Import(SubscriptionExpiryService.class)
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.MONITORING_LIST_CACHE);
        }
    }

    @MockitoBean
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionExpiryService subscriptionExpiryService;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void expireSubscriptions_캐시무효화_실제로_동작함() {
        // GIVEN: 캐시에 더미 값을 미리 저장
        Cache cache = cacheManager.getCache(CacheConfig.MONITORING_LIST_CACHE);
        assertThat(cache).isNotNull();
        cache.put("dummy-key", List.of("flight-A", "flight-B"));
        assertThat(cache.get("dummy-key")).isNotNull();

        given(subscriptionRepository.bulkExpireSubscriptions(
                SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED))
                .willReturn(1);

        // WHEN: expireSubscriptions() 호출 — @CacheEvict(allEntries = true) 발동
        subscriptionExpiryService.expireSubscriptions();

        // THEN: 캐시가 비워져야 한다
        assertThat(cache.get("dummy-key")).isNull();
    }
}
