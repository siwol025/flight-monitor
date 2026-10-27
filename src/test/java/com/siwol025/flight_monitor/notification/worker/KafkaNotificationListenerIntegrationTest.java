package com.siwol025.flight_monitor.notification.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.siwol025.flight_monitor.notification.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.notification.service.AlertConditionChecker;
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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({KafkaNotificationListener.class, SubscriptionService.class, RouteService.class, AlertConditionChecker.class,
        NotificationContentFormatter.class, KafkaNotificationListenerIntegrationTest.ClockConfig.class})
class KafkaNotificationListenerIntegrationTest {

    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 11, 26);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    // Kafka 경계
    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private KafkaNotificationListener listener;

    @Autowired
    private TestEntityManager em;

    @Test
    void 팬아웃_노선구독자중조건통과자만_메일작업발행() {
        Route economy = persistRoute(SeatGrade.ECONOMY);
        // 같은 구간·날짜의 비즈니스 노선은 다른 노선이다
        Route business = persistRoute(SeatGrade.BUSINESS);
        persistSubscription("plain", economy, null, null);                      // 조건 없음: 하락이면 알린다
        persistSubscription("target", economy, new BigDecimal("40.00"), null);   // 목표가 40 미달(45.67)
        persistSubscription("drop", economy, null, new BigDecimal("5.00"));      // 하락률 5% 충족(실제 8.66%)
        persistSubscription("business", business, null, null);
        persistSubscription("expired", economy, null, null).updateStatus(SubscriptionStatus.EXPIRED);
        em.flush();
        given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(CompletableFuture.completedFuture(null));

        listener.processNotification(priceDrop(economy.getId()));

        ArgumentCaptor<Object> tasks = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, times(2)).send(eq("email-send-tasks"), anyString(), tasks.capture());
        assertThat(tasks.getAllValues())
                .extracting(task -> ((EmailSendTaskDto) task).toEmail())
                .containsExactlyInAnyOrder("plain@test.com", "drop@test.com");
    }

    private Route persistRoute(SeatGrade seatGrade) {
        return em.persist(new Route(new RouteKey("ICN", "NRT", DEPARTURE_DATE, seatGrade)));
    }

    private Subscription persistSubscription(String name, Route route, BigDecimal targetPrice,
                                             BigDecimal dropThresholdPercent) {
        User user = em.persist(User.builder()
                .email(name + "@test.com")
                .name(name)
                .provider(Provider.GOOGLE)
                .providerId("google-" + name)
                .role(Role.USER)
                .build());
        return em.persist(Subscription.builder()
                .user(user)
                .route(route)
                .price(new BigDecimal("50.00"))
                .targetPrice(targetPrice)
                .dropThresholdPercent(dropThresholdPercent)
                .build());
    }

    private PriceDropNotificationDto priceDrop(Long routeId) {
        return PriceDropNotificationDto.builder()
                .routeId(routeId)
                .departureAirport("ICN")
                .arrivalAirport("NRT")
                .departureDate(DEPARTURE_DATE)
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(new BigDecimal("50.00"))
                .newPrice(new BigDecimal("45.67"))
                .currency("USD")
                .carrierCode("ZZ")
                .flightNumber("1079")
                .departingAt(LocalDateTime.of(2026, 11, 26, 19, 21))
                .arrivingAt(LocalDateTime.of(2026, 11, 26, 21, 36))
                .detectedAt(LocalDateTime.of(2026, 10, 26, 9, 1))
                .build();
    }
}
