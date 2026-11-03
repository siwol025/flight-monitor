package com.siwol025.flight_monitor.notification.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.google.common.util.concurrent.Uninterruptibles;
import com.siwol025.flight_monitor.global.config.RabbitMqConfig;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.notification.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.notification.mail.NotificationService;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.springframework.mail.MailSendException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 메일 작업 큐를 실제 RabbitMQ(compose와 같은 이미지)로 검사한다. 목은 시스템 경계인 발송(NotificationService)뿐이다.
 * 재시도는 3회·10ms 간격, 소비자는 1개로 줄여 순서와 시간을 결정적으로 만든다.
 */
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class EmailTaskQueueIntegrationTest {

    private static final int MAX_RETRIES = 3;

    @Container
    private static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3.7-management"));

    private final NotificationService notificationService = mock(NotificationService.class);

    private final EmailSendTaskDto task = new EmailSendTaskDto("user@test.com", "제목", "내용");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
            .withUserConfiguration(RabbitMqConfig.class, EmailTaskListener.class)
            .withBean(NotificationService.class, () -> notificationService)
            .withPropertyValues(
                    "spring.rabbitmq.host=" + RABBIT.getHost(),
                    "spring.rabbitmq.port=" + RABBIT.getAmqpPort(),
                    "spring.rabbitmq.username=" + RABBIT.getAdminUsername(),
                    "spring.rabbitmq.password=" + RABBIT.getAdminPassword(),
                    "notification.email-task.retry.max-retries=" + MAX_RETRIES,
                    "notification.email-task.retry.initial-interval-ms=10",
                    "notification.email-task.concurrency=1");

    @BeforeEach
    void purgeQueues() {
        // 컨테이너는 테스트 클래스가 공유하므로 앞 테스트가 남긴 메시지를 비운다
        contextRunner.run(context -> {
            AmqpAdmin admin = context.getBean(AmqpAdmin.class);
            admin.purgeQueue(RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE, false);
            admin.purgeQueue(RabbitMqConfig.EMAIL_SEND_TASKS_DLQ, false);
        });
    }

    @Test
    void 메일작업큐_일시오류N회후_성공하면_1회발송되고_DLQ비어있음(CapturedOutput output) {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger sent = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() <= 2) {
                throw new MailSendException("SMTP 장애");
            }
            sent.incrementAndGet();
            return null;
        }).when(notificationService).sendPriceDropNotification(any());

        contextRunner.run(context -> {
            publish(context);

            await().atMost(Duration.ofSeconds(10)).until(() -> sent.get() == 1);
            // 성공 뒤 같은 메시지가 다시 오지 않는지 잠시 지켜본다(ack 누락이면 재전달된다)
            Thread.sleep(500);

            assertThat(attempts.get()).isEqualTo(3);
            assertThat(sent.get()).isEqualTo(1);
            assertThat(messageCount(context, RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE)).isZero();
            assertThat(messageCount(context, RabbitMqConfig.EMAIL_SEND_TASKS_DLQ)).isZero();
            // 발행 때 메시지 id와 timestamp가 붙어야 실패·완료 로그를 같은 id로 잇고 지연을 잴 수 있다
            assertThat(output.getOut()).containsPattern(
                    "알림 전송 완료: email-send-tasks id=[0-9a-f-]{36} latency=\\d+ms");
        });
    }

    @Test
    void 메일작업큐_일시오류_재시도소진시_DLQ로이동() {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new MailSendException("SMTP 장애");
        }).when(notificationService).sendPriceDropNotification(any());

        contextRunner.run(context -> {
            publish(context);

            await().atMost(Duration.ofSeconds(10))
                    .until(() -> messageCount(context, RabbitMqConfig.EMAIL_SEND_TASKS_DLQ) == 1);

            // 최초 1회 + 재시도 MAX_RETRIES회
            assertThat(attempts.get()).isEqualTo(MAX_RETRIES + 1);
            assertThat(messageCount(context, RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE)).isZero();
        });
    }

    @Test
    void 메일작업큐_영구오류_재시도없이_DLQ로이동() {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new BadRequestException(ErrorTag.INVALID_INPUT);
        }).when(notificationService).sendPriceDropNotification(any());

        contextRunner.run(context -> {
            publish(context);

            await().atMost(Duration.ofSeconds(10))
                    .until(() -> messageCount(context, RabbitMqConfig.EMAIL_SEND_TASKS_DLQ) == 1);

            // 리스너 예외는 ListenerExecutionFailedException으로 감싸여 오므로 원인 체인으로 걸러져야 한다
            assertThat(attempts.get()).isEqualTo(1);
            assertThat(messageCount(context, RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE)).isZero();
        });
    }

    @Test
    void 메일작업큐_처리중_소비자종료시_미ack메시지가_재전달됨() {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch firstAttemptStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstAttempt = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                firstAttemptStarted.countDown();
                // 소비자가 종료될 때까지 발송 중으로 머문다(배포 재시작 중 처리 도중인 메일). 종료 때 컨테이너가 스레드를
                // 인터럽트하는데, 여기서 예외로 나가면 재시도가 같은 컨테이너에서 한 번 더 부르므로 인터럽트를 무시한다
                Uninterruptibles.awaitUninterruptibly(releaseFirstAttempt, 10, TimeUnit.SECONDS);
            }
            return null;
        }).when(notificationService).sendPriceDropNotification(any());

        contextRunner.run(context -> {
            publish(context);
            assertThat(firstAttemptStarted.await(10, TimeUnit.SECONDS)).isTrue();
        });
        // 컨텍스트 종료로 소비자 채널이 닫혔다. ack 전이었으므로 메시지는 큐로 돌아가 있어야 한다
        releaseFirstAttempt.countDown();

        contextRunner.run(context -> {
            await().atMost(Duration.ofSeconds(10)).until(() -> attempts.get() == 2);
            await().atMost(Duration.ofSeconds(5))
                    .until(() -> messageCount(context, RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE) == 0);
            assertThat(messageCount(context, RabbitMqConfig.EMAIL_SEND_TASKS_DLQ)).isZero();
        });
    }

    private void publish(AssertableApplicationContext context) {
        context.getBean(RabbitTemplate.class)
                .convertAndSend(RabbitMqConfig.NOTIFICATION_EXCHANGE, RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE, task);
    }

    private long messageCount(AssertableApplicationContext context, String queue) {
        return context.getBean(AmqpAdmin.class).getQueueInfo(queue).getMessageCount();
    }
}
