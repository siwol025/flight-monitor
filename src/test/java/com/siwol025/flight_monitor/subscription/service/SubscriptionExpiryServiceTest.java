package com.siwol025.flight_monitor.subscription.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpiryServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    // 한국 시각 2026-10-26 08:00(UTC로는 전날 23:00)
    @Spy private Clock clock = Clock.fixed(Instant.parse("2026-10-25T23:00:00Z"), ZoneId.of("Asia/Seoul"));

    @InjectMocks
    private SubscriptionExpiryService subscriptionExpiryService;

    @Test
    void expireSubscriptions_만료대상있음_bulkExpireSubscriptions_호출됨() {
        given(subscriptionRepository.bulkExpireSubscriptions(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED, LocalDate.of(2026, 10, 26)))
                .willReturn(1);

        subscriptionExpiryService.expireSubscriptions();

        then(subscriptionRepository).should().bulkExpireSubscriptions(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED, LocalDate.of(2026, 10, 26));
    }

    @Test
    void expireSubscriptions_만료대상없음_정상완료됨() {
        given(subscriptionRepository.bulkExpireSubscriptions(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED, LocalDate.of(2026, 10, 26)))
                .willReturn(0);

        assertThatCode(() -> subscriptionExpiryService.expireSubscriptions())
                .doesNotThrowAnyException();
    }

}
