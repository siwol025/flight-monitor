package com.siwol025.flight_monitor.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.siwol025.flight_monitor.fare.LowestFare;
import com.siwol025.flight_monitor.global.config.AsyncConfig;
import com.siwol025.flight_monitor.global.config.RabbitMqConfig;
import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import com.siwol025.flight_monitor.monitor.service.PriceMonitorServiceIntegrationTest.FakeLowestFareFinder;
import com.siwol025.flight_monitor.monitor.service.PriceMonitorServiceIntegrationTest.MutableClock;
import com.siwol025.flight_monitor.monitor.worker.RedisRateLimiter;
import com.siwol025.flight_monitor.notification.producer.NotificationProducer;
import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.route.repository.RouteLatestFareRepository;
import com.siwol025.flight_monitor.route.repository.RouteRepository;
import com.siwol025.flight_monitor.route.service.RouteService;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 감시가 실제 브로커로 가격 하락을 발행할 때, 브로커가 확인하지 못하면 최신가가 전진하지 않고 다음 관측에서 다시 발행되는지 본다.
 * 확인 뒤 최신가 갱신은 알림 executor 스레드에서 커밋되므로 테스트 트랜잭션을 쓰지 않고 직접 정리한다.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@ImportAutoConfiguration(RabbitAutoConfiguration.class)
@Import({PriceMonitorService.class, RouteLatestFareService.class, RouteService.class, SubscriptionService.class,
        NotificationProducer.class, RabbitMqConfig.class, AsyncConfig.class,
        PriceMonitorBrokerConfirmIntegrationTest.TestConfig.class})
class PriceMonitorBrokerConfirmIntegrationTest {

    private static final Instant FIRST_OBSERVED_AT = Instant.parse("2026-10-26T00:00:00Z");

    @Container
    private static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3.7-management"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("notification.price-drop.confirm-timeout", () -> "2s");
    }

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
        PipelineMetrics pipelineMetrics() {
            return new PipelineMetrics(new SimpleMeterRegistry());
        }
    }

    // Redis 경계(공유 레이트 리미터)
    @MockitoBean
    private RedisRateLimiter rateLimiter;

    @Autowired
    private PriceMonitorService priceMonitorService;

    @Autowired
    private RouteLatestFareRepository routeLatestFareRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private FakeLowestFareFinder fakeLowestFareFinder;

    @Autowired
    private MutableClock clock;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @AfterEach
    void cleanUp() {
        routeLatestFareRepository.deleteAll();
        routeRepository.deleteAll();
        amqpAdmin.purgeQueue(RabbitMqConfig.PRICE_DROP_EVENTS_QUEUE);
    }

    @Test
    void 브로커가확인하지못하면_최신가유지_복구뒤다음관측에서_다시발행하고_갱신() {
        Route route = routeRepository.save(new Route(new RouteKey("KIX", "BKK", LocalDate.of(2026, 12, 10), SeatGrade.ECONOMY)));
        fakeLowestFareFinder.respond(fare("50.00"));
        priceMonitorService.checkPriceAndNotify(route.getId());
        clock.advance(Duration.ofMinutes(1));
        fakeLowestFareFinder.respond(fare("45.67"));

        // 브로커가 멈추면 연결은 살아 있어 발행은 성공한 듯 돌아오지만 확인이 오지 않는다
        RABBIT.getDockerClient().pauseContainerCmd(RABBIT.getContainerId()).exec();
        try {
            priceMonitorService.checkPriceAndNotify(route.getId());
            // 확인 대기 상한(2초)이 지나도 최신가는 직전 가격 그대로다
            await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(latestAmount(route)).isEqualByComparingTo("50.00"));
        } finally {
            RABBIT.getDockerClient().unpauseContainerCmd(RABBIT.getContainerId()).exec();
        }

        // 다음 주기: 같은 하락을 다시 감지해 발행하고, 확인 뒤 최신가가 전진한다
        clock.advance(Duration.ofMinutes(1));
        priceMonitorService.checkPriceAndNotify(route.getId());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(latestAmount(route)).isEqualByComparingTo("45.67"));
        assertThat(routeLatestFareRepository.findByRouteId(route.getId()).orElseThrow().getObservedAt())
                .isEqualTo(Instant.parse("2026-10-26T00:02:00Z"));
    }

    private BigDecimal latestAmount(Route route) {
        return routeLatestFareRepository.findByRouteId(route.getId()).orElseThrow().getAmount();
    }

    private static LowestFare fare(String amount) {
        return new LowestFare(new BigDecimal(amount), "USD", "ZZ", "Duffel Airways", "1079",
                LocalDateTime.of(2026, 12, 10, 9, 0), LocalDateTime.of(2026, 12, 10, 13, 0));
    }
}
