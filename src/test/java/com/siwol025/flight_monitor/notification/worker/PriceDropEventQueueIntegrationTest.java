package com.siwol025.flight_monitor.notification.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.siwol025.flight_monitor.global.config.AsyncConfig;
import com.siwol025.flight_monitor.global.config.RabbitMqConfig;
import com.siwol025.flight_monitor.notification.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.notification.producer.NotificationProducer;
import com.siwol025.flight_monitor.notification.service.AlertConditionChecker;
import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.route.service.RouteService;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 가격 하락 이벤트를 실제 RabbitMQ로 발행해 팬아웃 리스너가 소비하고, 조건을 통과한 구독자 수만큼 메일 작업 큐에 쌓이는지 본다.
 * 브로커 배선(exchange·큐·리스너)과 이벤트 DTO의 직렬화 왕복을 검증한다. 메일 리스너는 올리지 않고 메일 작업 큐를 직접 읽는다.
 */
@DataJpaTest
@Testcontainers
@ImportAutoConfiguration(RabbitAutoConfiguration.class)
@Import({RabbitMqConfig.class, AsyncConfig.class, NotificationProducer.class, PriceDropFanoutListener.class, SubscriptionService.class,
        RouteService.class, AlertConditionChecker.class, NotificationContentFormatter.class,
        PriceDropEventQueueIntegrationTest.ClockConfig.class})
class PriceDropEventQueueIntegrationTest {

    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 11, 26);

    @Container
    private static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3.7-management"));

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    private NotificationProducer producer;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private TestEntityManager em;

    @Test
    void 가격하락이벤트_발행부터_메일작업큐까지_조건통과자수만큼_적재됨() {
        Route economy = persistRoute(SeatGrade.ECONOMY);
        Route business = persistRoute(SeatGrade.BUSINESS);
        persistSubscription("plain", economy, null, null);                      // 조건 없음: 하락이면 알린다
        persistSubscription("target", economy, new BigDecimal("40.00"), null);   // 목표가 40 미달(45.67)
        persistSubscription("drop", economy, null, new BigDecimal("5.00"));      // 하락률 5% 충족(실제 8.66%)
        persistSubscription("business", business, null, null);
        // 팬아웃 리스너는 다른 스레드·트랜잭션에서 구독자를 읽으므로 커밋해 둔다(테스트용 내장 DB는 이 컨텍스트 전용)
        TestTransaction.flagForCommit();
        TestTransaction.end();

        producer.publishPriceDrop(priceDrop(economy.getId()));

        await().atMost(Duration.ofSeconds(10))
                .until(() -> amqpAdmin.getQueueInfo(RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE).getMessageCount() == 2);
        List<EmailSendTaskDto> tasks = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tasks.add((EmailSendTaskDto) rabbitTemplate.receiveAndConvert(RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE, 1_000));
        }
        assertThat(tasks).extracting(EmailSendTaskDto::toEmail)
                .containsExactlyInAnyOrder("plain@test.com", "drop@test.com");
        // 메일 문구는 이벤트만으로 만든다: 직렬화 왕복 뒤에도 노선·가격·편명이 그대로여야 한다
        assertThat(tasks.get(0).content()).contains("45.67", "ZZ1079", "19:21");
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
