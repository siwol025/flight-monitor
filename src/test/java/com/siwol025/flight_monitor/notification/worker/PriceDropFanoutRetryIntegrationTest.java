package com.siwol025.flight_monitor.notification.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.siwol025.flight_monitor.global.config.RabbitMqConfig;
import com.siwol025.flight_monitor.notification.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.notification.service.AlertConditionChecker;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.SubscriberWithConditionDto;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.util.StringUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 가격 하락 이벤트 팬아웃이 실패하면 재시도하고, 계속 실패하면 DLQ로 보내는지 실제 RabbitMQ로 검사한다.
 * 목은 DB 경계인 구독자 조회(SubscriptionService)뿐이다. 재시도는 3회·10ms 간격으로 줄인다.
 */
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class PriceDropFanoutRetryIntegrationTest {

    private static final int MAX_RETRIES = 3;

    @Container
    private static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3.7-management"));

    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
            .withUserConfiguration(RabbitMqConfig.class, PriceDropFanoutListener.class, AlertConditionChecker.class,
                    NotificationContentFormatter.class)
            .withBean(SubscriptionService.class, () -> subscriptionService)
            .withPropertyValues(
                    "spring.rabbitmq.host=" + RABBIT.getHost(),
                    "spring.rabbitmq.port=" + RABBIT.getAmqpPort(),
                    "spring.rabbitmq.username=" + RABBIT.getAdminUsername(),
                    "spring.rabbitmq.password=" + RABBIT.getAdminPassword(),
                    "notification.price-drop-fanout.retry.max-retries=" + MAX_RETRIES,
                    "notification.price-drop-fanout.retry.initial-interval-ms=10");

    @BeforeEach
    void purgeQueues() {
        // 컨테이너는 테스트 클래스가 공유하므로 앞 테스트가 남긴 메시지를 비운다
        contextRunner.run(context -> {
            AmqpAdmin admin = context.getBean(AmqpAdmin.class);
            admin.purgeQueue(RabbitMqConfig.PRICE_DROP_EVENTS_QUEUE, false);
            admin.purgeQueue(RabbitMqConfig.PRICE_DROP_EVENTS_DLQ, false);
            admin.purgeQueue(RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE, false);
        });
    }

    @Test
    void 구독자조회_일시실패후_성공하면_메일작업발행되고_DLQ비어있음(CapturedOutput output) {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() <= 2) {
                throw new DataAccessResourceFailureException("DB 연결 실패");
            }
            return List.of(new SubscriberWithConditionDto("user@test.com", null, null));
        }).when(subscriptionService).getSubscribersWithCondition(anyLong());

        contextRunner.run(context -> {
            publish(context);

            await().atMost(Duration.ofSeconds(10))
                    .until(() -> messageCount(context, RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE) == 1);
            // 성공 뒤 같은 이벤트가 다시 처리되지 않는지 잠시 지켜본다(ack 누락이면 재전달된다)
            Thread.sleep(500);

            assertThat(attempts.get()).isEqualTo(3);
            assertThat(messageCount(context, RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE)).isEqualTo(1);
            EmailSendTaskDto task = (EmailSendTaskDto) context.getBean(RabbitTemplate.class)
                    .receiveAndConvert(RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE, 1_000);
            assertThat(task.toEmail()).isEqualTo("user@test.com");
            assertThat(messageCount(context, RabbitMqConfig.PRICE_DROP_EVENTS_QUEUE)).isZero();
            assertThat(messageCount(context, RabbitMqConfig.PRICE_DROP_EVENTS_DLQ)).isZero();
            // 시도마다 실패 로그가 남아야 재시도 경과를 로그로 셀 수 있다
            assertThat(failureLogCount(output)).isEqualTo(2);
        });
    }

    @Test
    void 구독자조회_계속실패하면_재시도소진후_DLQ로이동(CapturedOutput output) {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new DataAccessResourceFailureException("DB 연결 실패");
        }).when(subscriptionService).getSubscribersWithCondition(any());

        contextRunner.run(context -> {
            publish(context);

            await().atMost(Duration.ofSeconds(10))
                    .until(() -> messageCount(context, RabbitMqConfig.PRICE_DROP_EVENTS_DLQ) == 1);

            // 최초 1회 + 재시도 MAX_RETRIES회
            assertThat(attempts.get()).isEqualTo(MAX_RETRIES + 1);
            assertThat(messageCount(context, RabbitMqConfig.PRICE_DROP_EVENTS_QUEUE)).isZero();
            assertThat(messageCount(context, RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE)).isZero();
            assertThat(failureLogCount(output)).isEqualTo(MAX_RETRIES + 1);
            assertThat(output.getOut()).containsPattern("\\[PriceDropFanoutRetry] DLQ 이동: price-drop-events id=[0-9a-f-]{36}");
        });
    }

    private int failureLogCount(CapturedOutput output) {
        return StringUtils.countOccurrencesOf(output.getOut(), "[PriceDropFanoutRetry] 팬아웃 실패: routeId=7, id=");
    }

    private void publish(AssertableApplicationContext context) {
        PriceDropNotificationDto event = PriceDropNotificationDto.builder()
                .routeId(7L)
                .departureAirport("ICN")
                .arrivalAirport("NRT")
                .departureDate(LocalDate.of(2026, 11, 26))
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
        context.getBean(RabbitTemplate.class)
                .convertAndSend(RabbitMqConfig.NOTIFICATION_EXCHANGE, RabbitMqConfig.PRICE_DROP_EVENTS_QUEUE, event);
    }

    private long messageCount(AssertableApplicationContext context, String queue) {
        return context.getBean(AmqpAdmin.class).getQueueInfo(queue).getMessageCount();
    }
}
