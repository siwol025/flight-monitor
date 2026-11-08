package com.siwol025.flight_monitor.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.siwol025.flight_monitor.notification.producer.NotificationProducer;
import com.siwol025.flight_monitor.notification.service.AlertConditionChecker;
import com.siwol025.flight_monitor.notification.mail.NotificationService;
import com.siwol025.flight_monitor.notification.worker.EmailTaskListener;
import com.siwol025.flight_monitor.notification.worker.PriceDropFanoutListener;
import com.siwol025.flight_monitor.notification.worker.NotificationContentFormatter;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationBeanRegistrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            // 운영(Spring Boot)처럼 설정 기본값 "10s"를 Duration으로 바꿀 수 있게 한다
            .withInitializer(context -> context.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
            .withBean(SubscriptionService.class, () -> mock(SubscriptionService.class))
            .withBean(NotificationContentFormatter.class, () -> mock(NotificationContentFormatter.class))
            .withBean(AlertConditionChecker.class, () -> mock(AlertConditionChecker.class))
            .withBean(NotificationService.class, () -> mock(NotificationService.class))
            // 발행 확인 처리 executor(운영은 AsyncConfig)
            .withBean("notificationEventExecutor", Executor.class, () -> Runnable::run)
            .withUserConfiguration(NotificationProducer.class, PriceDropFanoutListener.class,
                    EmailTaskListener.class);

    @Test
    void 알림설정없이_발행자와리스너빈_등록됨() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(NotificationProducer.class)
                .hasSingleBean(PriceDropFanoutListener.class)
                .hasSingleBean(EmailTaskListener.class));
    }
}
