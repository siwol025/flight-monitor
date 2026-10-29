package com.siwol025.flight_monitor.monitor.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.siwol025.flight_monitor.monitor.utils.TaskQueueManager;
import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.route.service.RouteService;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({FlightMonitoringProducer.class, SubscriptionService.class, RouteService.class,
        FlightMonitoringProducerIntegrationTest.FixedClockConfig.class})
class FlightMonitoringProducerIntegrationTest {

    @TestConfiguration
    static class FixedClockConfig {
        // 한국 시각 2026-10-26 08:00(UTC로는 전날 23:00)
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-25T23:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    // Redis 큐 경계
    @MockitoBean
    private TaskQueueManager taskQueueManager;

    @Autowired
    private FlightMonitoringProducer producer;

    @Autowired
    private TestEntityManager em;

    @Test
    void 프로듀서_활성구독있는_활성미출발노선만_중복없이발행() {
        User alice = persistUser("alice");
        User bob = persistUser("bob");
        // 활성 구독 2건인 노선은 한 번만 발행한다
        Route shared = persistRoute("NRT", LocalDate.of(2026, 10, 27));
        persistSubscription(alice, shared);
        persistSubscription(bob, shared);
        // 만료된 구독만 있는 노선
        Route expiredOnly = persistRoute("KIX", LocalDate.of(2026, 10, 27));
        persistSubscription(alice, expiredOnly).updateStatus(SubscriptionStatus.EXPIRED);
        // 업체가 거부한 무효 노선
        Route invalid = persistRoute("FUK", LocalDate.of(2026, 10, 27));
        invalid.markInvalid();
        persistSubscription(alice, invalid);
        // 한국 날짜로 어제 출발(UTC 날짜로는 아직 오늘이라 통과해 버린다)
        Route departedYesterday = persistRoute("CTS", LocalDate.of(2026, 10, 25));
        persistSubscription(alice, departedYesterday);
        // 오늘 출발 노선은 그날이 끝날 때까지 감시한다
        Route departingToday = persistRoute("OKA", LocalDate.of(2026, 10, 26));
        persistSubscription(alice, departingToday);
        em.flush();

        producer.produceMonitoringTasks();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> payloads = ArgumentCaptor.forClass(List.class);
        verify(taskQueueManager).publishTasks(payloads.capture());
        // 큐 페이로드는 노선 ID 숫자 문자열이다
        assertThat(payloads.getValue()).containsExactlyInAnyOrder(
                String.valueOf(shared.getId()), String.valueOf(departingToday.getId()));
    }

    private User persistUser(String name) {
        return em.persist(User.builder()
                .email(name + "@test.com")
                .name(name)
                .provider(Provider.GOOGLE)
                .providerId("google-" + name)
                .role(Role.USER)
                .build());
    }

    private Route persistRoute(String arrivalAirport, LocalDate departureDate) {
        return em.persist(new Route(new RouteKey("ICN", arrivalAirport, departureDate, SeatGrade.ECONOMY)));
    }

    private Subscription persistSubscription(User user, Route route) {
        return em.persist(Subscription.builder()
                .user(user)
                .route(route)
                .build());
    }
}
