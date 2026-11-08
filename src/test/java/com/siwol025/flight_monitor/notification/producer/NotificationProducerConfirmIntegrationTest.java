package com.siwol025.flight_monitor.notification.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.siwol025.flight_monitor.global.config.RabbitMqConfig;
import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 가격 하락 이벤트 발행 결과(publisher confirm·mandatory return)가 실제 브로커 응답대로 완료되는지 본다.
 */
@SpringBootTest(classes = {RabbitMqConfig.class, NotificationProducer.class,
        NotificationProducerConfirmIntegrationTest.ExecutorConfig.class})
@ImportAutoConfiguration(RabbitAutoConfiguration.class)
@Testcontainers
class NotificationProducerConfirmIntegrationTest {

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
    static class ExecutorConfig {
        // 완료 스레드를 이름으로 구분하려고 한 스레드짜리 알림 executor를 쓴다
        @Bean(name = "notificationEventExecutor", destroyMethod = "shutdownNow")
        ExecutorService notificationEventExecutor() {
            return Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "notification-test"));
        }
    }

    @Autowired
    private NotificationProducer producer;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private ExecutorService notificationEventExecutor;

    @AfterEach
    void redeclareTopology() {
        ((RabbitAdmin) amqpAdmin).initialize();
        amqpAdmin.purgeQueue(RabbitMqConfig.PRICE_DROP_EVENTS_QUEUE);
    }

    @Test
    void 브로커가확인하면_정상완료_완료는알림executor에서() throws Exception {
        // executor를 잠시 막아 두어, 확인이 먼저 와도 완료가 executor에서만 일어날 수 있게 한다
        CountDownLatch release = new CountDownLatch(1);
        notificationEventExecutor.execute(() -> awaitQuietly(release));
        CompletableFuture<String> completedOn = new CompletableFuture<>();

        producer.publishPriceDrop(priceDrop())
                .whenComplete((ignored, failure) -> completedOn.complete(
                        failure == null ? Thread.currentThread().getName() : "failed: " + failure));
        release.countDown();

        assertThat(completedOn.get(10, TimeUnit.SECONDS)).isEqualTo("notification-test");
        assertThat(amqpAdmin.getQueueInfo(RabbitMqConfig.PRICE_DROP_EVENTS_QUEUE).getMessageCount()).isEqualTo(1);
    }

    @Test
    void 큐가없어_라우팅되지않으면_실패완료() {
        amqpAdmin.deleteQueue(RabbitMqConfig.PRICE_DROP_EVENTS_QUEUE);

        Throwable failure = catchThrowable(() ->
                producer.publishPriceDrop(priceDrop()).toCompletableFuture().get(10, TimeUnit.SECONDS));

        // 브로커는 라우팅되지 않은 메시지도 ack한다. mandatory return을 보고 실패로 판정해야 한다
        assertThat(failure).hasCauseInstanceOf(PriceDropPublishException.class);
    }

    private static PriceDropNotificationDto priceDrop() {
        return PriceDropNotificationDto.builder().routeId(1L).build();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
