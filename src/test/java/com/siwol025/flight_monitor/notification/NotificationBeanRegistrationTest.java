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
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationBeanRegistrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
            .withBean(SubscriptionService.class, () -> mock(SubscriptionService.class))
            .withBean(NotificationContentFormatter.class, () -> mock(NotificationContentFormatter.class))
            .withBean(AlertConditionChecker.class, () -> mock(AlertConditionChecker.class))
            .withBean(NotificationService.class, () -> mock(NotificationService.class))
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
