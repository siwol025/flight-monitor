package com.siwol025.flight_monitor.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import com.siwol025.flight_monitor.fare.LowestFareFinder;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.RouteLatestFare;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.route.repository.RouteRepository;
import com.siwol025.flight_monitor.route.service.RouteService;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.dto.response.FareStatus;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionDetailResponse;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionResponse;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.AfterTransaction;

@DataJpaTest
@Import({SubscriptionService.class, RouteService.class, SubscriptionServiceIntegrationTest.FixedClockConfig.class})
class SubscriptionServiceIntegrationTest {

    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 11, 26);

    @TestConfiguration
    static class FixedClockConfig {
        // 한국 시각 2026-10-26 08:00(UTC로는 전날 23:00)
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-25T23:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    // 구독 등록·조회가 외부 운임 포트를 부르지 않는지 확인하는 용도로만 둔다
    @MockitoBean
    private LowestFareFinder lowestFareFinder;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private TestEntityManager em;

    private User user;

    @BeforeEach
    void setUp() {
        user = em.persist(User.builder()
                .email("route@test.com")
                .name("노선구독자")
                .provider(Provider.GOOGLE)
                .providerId("google-route")
                .role(Role.USER)
                .build());
    }

    @AfterTransaction
    void deleteCommittedRoutes() {
        // 새 노선은 RouteService가 별도 트랜잭션(REQUIRES_NEW)에서 커밋하므로 테스트 롤백에 포함되지 않는다.
        // @AfterEach는 테스트 트랜잭션 안에서 돌아 삭제도 함께 롤백되므로, 트랜잭션이 끝난 뒤에 지운다
        routeRepository.deleteAllInBatch();
    }

    @Test
    void 구독_새노선_노선생성_측정대기로저장_외부호출없음() {
        subscriptionService.subscribe(user, "icn", "nrt", DEPARTURE_DATE, SeatGrade.ECONOMY, null, null);

        assertThat(subscriptionService.getSubscriptions(user)).singleElement().satisfies(subscription -> {
            assertThat(subscription.departureAirportCode()).isEqualTo("ICN");
            assertThat(subscription.arrivalAirportCode()).isEqualTo("NRT");
            assertThat(subscription.departureDate()).isEqualTo(LocalDate.of(2026, 11, 26));
            assertThat(subscription.seatGrade()).isEqualTo(SeatGrade.ECONOMY);
            assertThat(subscription.subscribedPrice()).isNull();
            assertThat(subscription.fareStatus()).isEqualTo(FareStatus.AWAITING_MEASUREMENT);
        });
        verifyNoInteractions(lowestFareFinder);
    }

    @Test
    void 구독_기존노선에최신최저가있음_기준가로사용() {
        Route economy = em.persist(new Route(new RouteKey("ICN", "NRT", DEPARTURE_DATE, SeatGrade.ECONOMY)));
        em.persist(latestFare(economy, "45.67"));
        // 같은 구간·날짜라도 좌석등급이 다르면 다른 노선이다
        Route business = em.persist(new Route(new RouteKey("ICN", "NRT", DEPARTURE_DATE, SeatGrade.BUSINESS)));
        em.persist(latestFare(business, "300.00"));
        em.flush();

        subscriptionService.subscribe(user, "ICN", "NRT", DEPARTURE_DATE, SeatGrade.ECONOMY, null, null);

        assertThat(subscriptionService.getSubscriptions(user)).singleElement().satisfies(subscription -> {
            assertThat(subscription.subscribedPrice()).isEqualByComparingTo("45.67");
            assertThat(subscription.latestPrice()).isEqualByComparingTo("45.67");
            assertThat(subscription.currency()).isEqualTo("USD");
            assertThat(subscription.fareStatus()).isEqualTo(FareStatus.MEASURED);
        });
    }

    @Test
    void 구독_같은사용자같은노선_중복구독예외() {
        em.persist(new Route(new RouteKey("ICN", "NRT", DEPARTURE_DATE, SeatGrade.ECONOMY)));
        em.flush();
        subscriptionService.subscribe(user, "ICN", "NRT", DEPARTURE_DATE, SeatGrade.ECONOMY, null, null);

        // 소문자로 다시 요청해도 정규화된 같은 노선이다
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                subscriptionService.subscribe(user, "icn", "nrt", DEPARTURE_DATE, SeatGrade.ECONOMY, null, null));

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.DUPLICATE_SUBSCRIPTION);
        assertThat(ex.getStatus().value()).isEqualTo(400);
        assertThat(subscriptionService.getSubscriptions(user)).hasSize(1);
    }

    @Test
    void 구독_출발지도착지동일_400() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                subscriptionService.subscribe(user, "ICN", "icn", DEPARTURE_DATE, SeatGrade.ECONOMY, null, null));

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.SAME_DEPARTURE_ARRIVAL_AIRPORT);
        assertThat(ex.getStatus().value()).isEqualTo(400);
        assertThat(subscriptionService.getSubscriptions(user)).isEmpty();
    }

    @Test
    void 구독_과거출발일_400() {
        // 한국 시각 10-26 08:00에 10-25는 지난 날짜다(UTC 기준으로는 아직 10-25라 통과해 버린다)
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                subscriptionService.subscribe(user, "ICN", "NRT", LocalDate.of(2026, 10, 25), SeatGrade.ECONOMY, null, null));

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.PAST_DEPARTURE_DATE);
        assertThat(ex.getStatus().value()).isEqualTo(400);
        assertThat(subscriptionService.getSubscriptions(user)).isEmpty();
    }

    @Test
    void 구독_공항코드형식오류_400() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                subscriptionService.subscribe(user, "IC", "NRT", DEPARTURE_DATE, SeatGrade.ECONOMY, null, null));

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.INVALID_AIRPORT_CODE);
        assertThat(ex.getStatus().value()).isEqualTo(400);
        assertThat(subscriptionService.getSubscriptions(user)).isEmpty();
    }

    @Test
    void 구독_무효노선_400() {
        // 업체가 거부한 노선은 감시 대상이 아니라 구독해도 가격이 영원히 채워지지 않는다
        Route route = em.persist(new Route(new RouteKey("ICN", "NRT", DEPARTURE_DATE, SeatGrade.ECONOMY)));
        route.markInvalid();
        em.flush();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                subscriptionService.subscribe(user, "ICN", "NRT", DEPARTURE_DATE, SeatGrade.ECONOMY, null, null));

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.INVALID_ROUTE_SUBSCRIPTION);
        assertThat(ex.getStatus().value()).isEqualTo(400);
        assertThat(subscriptionService.getSubscriptions(user)).isEmpty();
    }

    @Test
    void 구독조회_측정대기_상태표시() {
        em.persist(new Route(new RouteKey("ICN", "NRT", DEPARTURE_DATE, SeatGrade.ECONOMY)));
        em.flush();
        subscriptionService.subscribe(user, "ICN", "NRT", DEPARTURE_DATE, SeatGrade.ECONOMY, null, null);

        SubscriptionResponse listed = subscriptionService.getSubscriptions(user).getFirst();
        assertThat(listed.fareStatus()).isEqualTo(FareStatus.AWAITING_MEASUREMENT);
        assertThat(listed.latestPrice()).isNull();
        assertThat(listed.currency()).isNull();

        SubscriptionDetailResponse detail = subscriptionService.getSubscription(user, listed.subscriptionId());
        assertThat(detail.fareStatus()).isEqualTo(FareStatus.AWAITING_MEASUREMENT);
        assertThat(detail.subscribedPrice()).isNull();
        assertThat(detail.latestPrice()).isNull();
        assertThat(detail.currency()).isNull();
        assertThat(detail.priceDifference()).isNull();
    }

    @Test
    void 구독조회_노선무효_상태표시() {
        Route route = em.persist(new Route(new RouteKey("ICN", "NRT", DEPARTURE_DATE, SeatGrade.ECONOMY)));
        em.persist(latestFare(route, "45.67"));
        em.flush();
        subscriptionService.subscribe(user, "ICN", "NRT", DEPARTURE_DATE, SeatGrade.ECONOMY, null, null);
        // 감시가 업체의 잘못된 노선 응답(422)으로 무효 처리한 상태. 최신 최저가가 남아 있어도 무효가 우선이다
        route.markInvalid();
        em.flush();

        SubscriptionResponse listed = subscriptionService.getSubscriptions(user).getFirst();
        assertThat(listed.fareStatus()).isEqualTo(FareStatus.ROUTE_INVALID);
        assertThat(subscriptionService.getSubscription(user, listed.subscriptionId()).fareStatus())
                .isEqualTo(FareStatus.ROUTE_INVALID);
    }

    @Test
    void 구독상세_저장된최신최저가와통화_반환_외부호출없음() {
        Route route = em.persist(new Route(new RouteKey("ICN", "NRT", DEPARTURE_DATE, SeatGrade.ECONOMY)));
        em.persist(latestFare(route, "45.67"));
        // 구독 뒤 최저가가 50.00에서 45.67로 내려간 상태(최신 최저가 갱신은 감시 쪽 책임이라 여기서는 직접 만든다)
        Subscription subscription = em.persist(Subscription.builder()
                .user(user)
                .route(route)
                .price(new BigDecimal("50.00"))
                .build());
        em.flush();

        SubscriptionDetailResponse detail = subscriptionService.getSubscription(user, subscription.getId());

        assertThat(detail.subscribedPrice()).isEqualByComparingTo("50.00");
        assertThat(detail.latestPrice()).isEqualByComparingTo("45.67");
        assertThat(detail.currency()).isEqualTo("USD");
        assertThat(detail.carrierCode()).isEqualTo("ZZ");
        assertThat(detail.carrierName()).isEqualTo("Duffel Airways");
        assertThat(detail.flightNumber()).isEqualTo("1079");
        assertThat(detail.departingAt()).isEqualTo(LocalDateTime.of(2026, 11, 26, 19, 21));
        assertThat(detail.arrivingAt()).isEqualTo(LocalDateTime.of(2026, 11, 26, 21, 36));
        assertThat(detail.observedAt()).isEqualTo(Instant.parse("2026-10-26T00:00:00Z"));
        assertThat(detail.priceDifference()).isEqualByComparingTo("-4.33");
        assertThat(detail.fareStatus()).isEqualTo(FareStatus.MEASURED);
        verifyNoInteractions(lowestFareFinder);
    }

    private RouteLatestFare latestFare(Route route, String amount) {
        return RouteLatestFare.builder()
                .route(route)
                .amount(new BigDecimal(amount))
                .currency("USD")
                .carrierCode("ZZ")
                .carrierName("Duffel Airways")
                .flightNumber("1079")
                .departingAt(LocalDateTime.of(2026, 11, 26, 19, 21))
                .arrivingAt(LocalDateTime.of(2026, 11, 26, 21, 36))
                .observedAt(Instant.parse("2026-10-26T00:00:00Z"))
                .build();
    }
}
