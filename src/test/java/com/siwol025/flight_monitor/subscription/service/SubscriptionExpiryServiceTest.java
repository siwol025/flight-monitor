package com.siwol025.flight_monitor.subscription.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpiryServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;

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
    void expireSubscriptions_만료대상없음_정상완료됨() {
        given(subscriptionRepository.bulkExpireSubscriptions(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED))
                .willReturn(0);

        assertThatCode(() -> subscriptionExpiryService.expireSubscriptions())
                .doesNotThrowAnyException();
    }

}
