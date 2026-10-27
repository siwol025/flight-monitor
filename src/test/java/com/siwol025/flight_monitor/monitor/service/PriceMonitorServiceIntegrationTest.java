package com.siwol025.flight_monitor.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.siwol025.flight_monitor.fare.FareProviderConfigurationException;
import com.siwol025.flight_monitor.fare.FareProviderException;
import com.siwol025.flight_monitor.fare.FareProviderUnavailableException;
import com.siwol025.flight_monitor.fare.FareRateLimitedException;
import com.siwol025.flight_monitor.fare.InvalidRouteException;
import com.siwol025.flight_monitor.fare.LowestFare;
import com.siwol025.flight_monitor.fare.LowestFareFinder;
import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.notification.producer.NotificationPublisher;
import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.RouteLatestFare;
import com.siwol025.flight_monitor.route.domain.RouteStatus;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.route.repository.RouteLatestFareRepository;
import com.siwol025.flight_monitor.route.repository.RouteRepository;
import com.siwol025.flight_monitor.route.service.RouteService;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({PriceMonitorService.class, RouteLatestFareService.class, RouteService.class, SubscriptionService.class,
        PriceMonitorServiceIntegrationTest.TestConfig.class})
class PriceMonitorServiceIntegrationTest {

    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 11, 26);
    private static final Instant FIRST_OBSERVED_AT = Instant.parse("2026-10-26T00:00:00Z");

    @TestConfiguration
    static class TestConfig {

        @Bean
        FakeLowestFareFinder fakeLowestFareFinder() {
            return new FakeLowestFareFinder();
        }

        @Bean
        MutableClock clock() {
            return new MutableClock(FIRST_OBSERVED_AT);
        }

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        PipelineMetrics pipelineMetrics(SimpleMeterRegistry meterRegistry) {
            return new PipelineMetrics(meterRegistry);
        }
    }

    // Kafka 경계
    @MockitoBean
    private NotificationPublisher notificationPublisher;

    @Autowired
    private PriceMonitorService priceMonitorService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private RouteLatestFareRepository routeLatestFareRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private FakeLowestFareFinder fakeLowestFareFinder;

    @Autowired
    private MutableClock clock;

    @Autowired
    private SimpleMeterRegistry meterRegistry;

    @Autowired
    private TestEntityManager em;

    private User user;
    private Route route;

    @BeforeEach
    void setUp() {
        fakeLowestFareFinder.respondEmpty();
        clock.set(FIRST_OBSERVED_AT);
        user = em.persist(User.builder()
                .email("monitor@test.com")
                .name("감시구독자")
                .provider(Provider.GOOGLE)
                .providerId("google-monitor")
                .role(Role.USER)
                .build());
        route = em.persist(new Route(new RouteKey("ICN", "NRT", DEPARTURE_DATE, SeatGrade.ECONOMY)));
        em.flush();
    }

    @Test
    void 감시_첫관측_최신최저가저장_측정대기구독기준가채움_알림없음() {
        Long pendingSubscriptionId = persistSubscription(null).getId();
        fakeLowestFareFinder.respond(fare("45.67", "USD"));

        priceMonitorService.checkPriceAndNotify(route.getId());

        RouteLatestFare saved = routeLatestFareRepository.findByRouteId(route.getId()).orElseThrow();
        assertThat(saved.getAmount()).isEqualByComparingTo("45.67");
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getCarrierCode()).isEqualTo("ZZ");
        assertThat(saved.getCarrierName()).isEqualTo("Duffel Airways");
        assertThat(saved.getFlightNumber()).isEqualTo("1079");
        assertThat(saved.getDepartingAt()).isEqualTo(LocalDateTime.of(2026, 11, 26, 19, 21));
        assertThat(saved.getArrivingAt()).isEqualTo(LocalDateTime.of(2026, 11, 26, 21, 36));
        assertThat(saved.getObservedAt()).isEqualTo(Instant.parse("2026-10-26T00:00:00Z"));
        assertThat(subscriptionService.getSubscription(user, pendingSubscriptionId).subscribedPrice())
                .isEqualByComparingTo("45.67");
        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void 감시_가격동일_관측시각만갱신_알림없음() {
        fakeLowestFareFinder.respond(fare("45.67", "USD"));
        priceMonitorService.checkPriceAndNotify(route.getId());
        clock.advance(Duration.ofMinutes(1));
        // 같은 금액이면 최저가 항공편이 바뀌어도 금액·항공편은 그대로 두고 확인한 시각만 앞으로 간다
        fakeLowestFareFinder.respond(fare("45.67", "USD", "KE", "Korean Air", "701"));

        priceMonitorService.checkPriceAndNotify(route.getId());

        RouteLatestFare latest = routeLatestFareRepository.findByRouteId(route.getId()).orElseThrow();
        assertThat(latest.getObservedAt()).isEqualTo(Instant.parse("2026-10-26T00:01:00Z"));
        assertThat(latest.getAmount()).isEqualByComparingTo("45.67");
        assertThat(latest.getCarrierCode()).isEqualTo("ZZ");
        assertThat(latest.getFlightNumber()).isEqualTo("1079");
        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void 감시_측정대기구독_다음관측에서기준가채움() {
        // 등록이 "최신가 없음"을 읽고 커밋하기 전에 첫 관측이 끝난 경합의 결과: 최신가는 있는데 기준가가 빈 구독
        em.persist(RouteLatestFare.builder()
                .route(route)
                .amount(new BigDecimal("45.67"))
                .currency("USD")
                .carrierCode("ZZ")
                .carrierName("Duffel Airways")
                .flightNumber("1079")
                .departingAt(LocalDateTime.of(2026, 11, 26, 19, 21))
                .arrivingAt(LocalDateTime.of(2026, 11, 26, 21, 36))
                .observedAt(FIRST_OBSERVED_AT)
                .build());
        Long pendingSubscriptionId = persistSubscription(null).getId();
        clock.advance(Duration.ofMinutes(1));
        // 가격이 그대로여도(첫 관측이 아니어도) 채운다
        fakeLowestFareFinder.respond(fare("45.67", "USD"));

        priceMonitorService.checkPriceAndNotify(route.getId());

        assertThat(subscriptionService.getSubscription(user, pendingSubscriptionId).subscribedPrice())
                .isEqualByComparingTo("45.67");
    }

    @Test
    void 가격비교_자릿수다른동일금액_변화없음() {
        fakeLowestFareFinder.respond(fare("45.00", "USD"));
        priceMonitorService.checkPriceAndNotify(route.getId());
        // 같은 금액이 소수 자릿수만 다르게 오는 경우. 다르게 보면 매 주기 가짜 변동(최저가 갱신·상승/하락)이 생긴다.
        // 저장 금액은 컬럼 scale로 다시 45.00이 되므로, 변동으로 보면 바뀌는 항공편 정보로 확인한다
        fakeLowestFareFinder.respond(fare("45", "USD", "KE", "Korean Air", "701"));

        priceMonitorService.checkPriceAndNotify(route.getId());

        RouteLatestFare latest = routeLatestFareRepository.findByRouteId(route.getId()).orElseThrow();
        assertThat(latest.getCarrierCode()).isEqualTo("ZZ");
        assertThat(latest.getFlightNumber()).isEqualTo("1079");
        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void 감시_가격상승_갱신_알림없음() {
        fakeLowestFareFinder.respond(fare("45.67", "USD"));
        priceMonitorService.checkPriceAndNotify(route.getId());
        clock.advance(Duration.ofMinutes(1));
        fakeLowestFareFinder.respond(fare("52.10", "USD", "KE", "Korean Air", "701"));

        priceMonitorService.checkPriceAndNotify(route.getId());

        RouteLatestFare latest = routeLatestFareRepository.findByRouteId(route.getId()).orElseThrow();
        assertThat(latest.getAmount()).isEqualByComparingTo("52.10");
        assertThat(latest.getCarrierCode()).isEqualTo("KE");
        assertThat(latest.getCarrierName()).isEqualTo("Korean Air");
        assertThat(latest.getFlightNumber()).isEqualTo("701");
        assertThat(latest.getObservedAt()).isEqualTo(Instant.parse("2026-10-26T00:01:00Z"));
        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void 감시_가격하락_갱신후_이벤트발행() {
        fakeLowestFareFinder.respond(fare("50.00", "USD"));
        priceMonitorService.checkPriceAndNotify(route.getId());
        clock.advance(Duration.ofMinutes(1));
        fakeLowestFareFinder.respond(fare("45.67", "USD"));
        // 이벤트를 발행하는 시점에 최신 최저가가 이미 새 가격이어야 한다
        AtomicReference<BigDecimal> storedAtPublish = new AtomicReference<>();
        willAnswer(invocation -> {
            storedAtPublish.set(routeLatestFareRepository.findByRouteId(route.getId()).orElseThrow().getAmount());
            return null;
        }).given(notificationPublisher).publishPriceDrop(any());

        priceMonitorService.checkPriceAndNotify(route.getId());

        ArgumentCaptor<PriceDropNotificationDto> captor = ArgumentCaptor.forClass(PriceDropNotificationDto.class);
        verify(notificationPublisher).publishPriceDrop(captor.capture());
        PriceDropNotificationDto event = captor.getValue();
        assertThat(event.routeId()).isEqualTo(route.getId());
        assertThat(event.departureAirport()).isEqualTo("ICN");
        assertThat(event.arrivalAirport()).isEqualTo("NRT");
        assertThat(event.departureDate()).isEqualTo(LocalDate.of(2026, 11, 26));
        assertThat(event.seatGrade()).isEqualTo(SeatGrade.ECONOMY);
        assertThat(event.oldPrice()).isEqualByComparingTo("50.00");
        assertThat(event.newPrice()).isEqualByComparingTo("45.67");
        assertThat(event.currency()).isEqualTo("USD");
        assertThat(event.carrierCode()).isEqualTo("ZZ");
        assertThat(event.flightNumber()).isEqualTo("1079");
        assertThat(event.departingAt()).isEqualTo(LocalDateTime.of(2026, 11, 26, 19, 21));
        assertThat(event.arrivingAt()).isEqualTo(LocalDateTime.of(2026, 11, 26, 21, 36));
        // 한국 시각(2026-10-26T00:01Z = 09:01)
        assertThat(event.detectedAt()).isEqualTo(LocalDateTime.of(2026, 10, 26, 9, 1));
        assertThat(storedAtPublish.get()).isEqualByComparingTo("45.67");
    }

    @Test
    void 감시_통화변경_새기준갱신_알림없음() {
        fakeLowestFareFinder.respond(fare("50.00", "USD"));
        priceMonitorService.checkPriceAndNotify(route.getId());
        clock.advance(Duration.ofMinutes(1));
        // 숫자로는 내려갔지만 통화가 달라 비교할 수 없다. 새 통화의 값이 다음 비교의 기준이 된다
        fakeLowestFareFinder.respond(fare("45.00", "EUR"));

        priceMonitorService.checkPriceAndNotify(route.getId());

        RouteLatestFare latest = routeLatestFareRepository.findByRouteId(route.getId()).orElseThrow();
        assertThat(latest.getAmount()).isEqualByComparingTo("45.00");
        assertThat(latest.getCurrency()).isEqualTo("EUR");
        assertThat(latest.getObservedAt()).isEqualTo(Instant.parse("2026-10-26T00:01:00Z"));
        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void 감시_운임없음_마지막값유지() {
        fakeLowestFareFinder.respond(fare("45.67", "USD"));
        priceMonitorService.checkPriceAndNotify(route.getId());
        clock.advance(Duration.ofMinutes(1));
        fakeLowestFareFinder.respondEmpty();

        priceMonitorService.checkPriceAndNotify(route.getId());

        RouteLatestFare latest = routeLatestFareRepository.findByRouteId(route.getId()).orElseThrow();
        assertThat(latest.getAmount()).isEqualByComparingTo("45.67");
        assertThat(latest.getObservedAt()).isEqualTo(Instant.parse("2026-10-26T00:00:00Z"));
        verifyNoInteractions(notificationPublisher);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("providerFailures")
    void 감시_업체장애_저장안함(FareProviderException failure) {
        fakeLowestFareFinder.respond(fare("45.67", "USD"));
        priceMonitorService.checkPriceAndNotify(route.getId());
        clock.advance(Duration.ofMinutes(1));
        fakeLowestFareFinder.fail(failure);

        priceMonitorService.checkPriceAndNotify(route.getId());

        RouteLatestFare latest = routeLatestFareRepository.findByRouteId(route.getId()).orElseThrow();
        assertThat(latest.getAmount()).isEqualByComparingTo("45.67");
        assertThat(latest.getObservedAt()).isEqualTo(Instant.parse("2026-10-26T00:00:00Z"));
        // 노선 자체의 문제(422)가 아니므로 무효 처리하지 않는다
        assertThat(routeRepository.findById(route.getId()).orElseThrow().getStatus()).isEqualTo(RouteStatus.ACTIVE);
        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void 감시_잘못된노선_노선무효처리_다음발행에서제외() {
        fakeLowestFareFinder.fail(new InvalidRouteException("업체가 노선을 거부함(422)"));

        priceMonitorService.checkPriceAndNotify(route.getId());

        assertThat(routeRepository.findById(route.getId()).orElseThrow().getStatus()).isEqualTo(RouteStatus.INVALID);
        // 큐에 남아 있던 같은 노선 작업은 포트를 부르지 않는다(예산 보호). 프로듀서의 발행 제외는 프로듀서 테스트에서 확인한다
        priceMonitorService.checkPriceAndNotify(route.getId());
        assertThat(fakeLowestFareFinder.calls()).isEqualTo(1);
        assertThat(routeLatestFareRepository.findByRouteId(route.getId())).isEmpty();
        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void 감시_업체호출_지연지표기록() {
        // 외부 호출 지연 지표는 max-in-flight 산정식(p99)의 출처라 실패한 호출의 지연도 남긴다
        Timer externalApiLatency = meterRegistry.get(PipelineMetrics.METRIC_EXTERNAL_API_LATENCY).timer();
        long before = externalApiLatency.count();
        fakeLowestFareFinder.respond(fare("45.67", "USD"));
        priceMonitorService.checkPriceAndNotify(route.getId());
        fakeLowestFareFinder.fail(new FareProviderUnavailableException("업체 장애"));

        priceMonitorService.checkPriceAndNotify(route.getId());

        assertThat(externalApiLatency.count() - before).isEqualTo(2);
    }

    static Stream<FareProviderException> providerFailures() {
        return Stream.of(
                new FareProviderUnavailableException("업체 장애(타임아웃·5xx·연결 실패)"),
                new FareRateLimitedException(Instant.parse("2026-10-26T00:02:00Z"), "호출 한도 초과(429)"),
                new FareProviderConfigurationException("인증 실패(401)"));
    }

    private Subscription persistSubscription(BigDecimal baselinePrice) {
        Subscription subscription = em.persist(Subscription.builder()
                .user(user)
                .route(route)
                .price(baselinePrice)
                .build());
        em.flush();
        return subscription;
    }

    private static LowestFare fare(String amount, String currency) {
        return fare(amount, currency, "ZZ", "Duffel Airways", "1079");
    }

    private static LowestFare fare(String amount, String currency, String carrierCode, String carrierName,
                                   String flightNumber) {
        return new LowestFare(new BigDecimal(amount), currency, carrierCode, carrierName, flightNumber,
                LocalDateTime.of(2026, 11, 26, 19, 21), LocalDateTime.of(2026, 11, 26, 21, 36));
    }

    /**
     * 테스트 전용 가짜 운임 포트. 다음 응답(최저가, 운임 없음, 업체 예외)을 정하고 호출 수를 센다.
     */
    static final class FakeLowestFareFinder implements LowestFareFinder {

        private Supplier<Optional<LowestFare>> next = Optional::empty;
        private int calls;

        void respond(LowestFare fare) {
            next = () -> Optional.of(fare);
        }

        void respondEmpty() {
            next = Optional::empty;
            calls = 0;
        }

        void fail(RuntimeException exception) {
            next = () -> {
                throw exception;
            };
        }

        int calls() {
            return calls;
        }

        @Override
        public Optional<LowestFare> findLowestFare(RouteKey routeKey) {
            calls++;
            return next.get();
        }
    }

    /**
     * 앞으로 감을 수 있는 시계. 관측 시각 갱신을 확인한다.
     */
    static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
