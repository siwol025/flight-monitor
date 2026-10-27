package com.siwol025.flight_monitor.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 만료의 "오늘"은 한국 날짜다. 한국 시각 10-26 08:00은 UTC로 아직 10-25라 경계가 갈린다.
 */
@DataJpaTest
@Import({SubscriptionExpiryService.class, SubscriptionExpiryServiceDateTest.FixedClockConfig.class})
class SubscriptionExpiryServiceDateTest {

    @TestConfiguration
    static class FixedClockConfig {
        // 한국 시각 2026-10-26 08:00(UTC로는 전날 23:00)
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-25T23:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired
    private SubscriptionExpiryService subscriptionExpiryService;

    @Autowired
    private TestEntityManager em;

    @Test
    void 만료_출발일지난노선구독_만료() {
        // UTC 날짜(10-25)를 쓰면 아직 오늘이라 남는다
        Long subscriptionId = persistSubscription(LocalDate.of(2026, 10, 25)).getId();

        subscriptionExpiryService.expireSubscriptions();

        assertThat(em.find(Subscription.class, subscriptionId).getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void 만료_출발일당일_만료안됨() {
        // 출발일 당일 노선은 그날이 끝날 때까지 감시한다
        Long subscriptionId = persistSubscription(LocalDate.of(2026, 10, 26)).getId();

        subscriptionExpiryService.expireSubscriptions();

        assertThat(em.find(Subscription.class, subscriptionId).getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    private Subscription persistSubscription(LocalDate departureDate) {
        User user = em.persist(User.builder()
                .email("expiry@test.com")
                .name("만료구독자")
                .provider(Provider.GOOGLE)
                .providerId("google-expiry")
                .role(Role.USER)
                .build());
        Route route = em.persist(new Route(new RouteKey("ICN", "NRT", departureDate, SeatGrade.ECONOMY)));
        Subscription subscription = em.persist(Subscription.builder()
                .user(user)
                .route(route)
                .build());
        em.flush();
        return subscription;
    }
}
