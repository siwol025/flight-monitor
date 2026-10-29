package com.siwol025.flight_monitor.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.siwol025.flight_monitor.notification.producer.NotificationProducer;
import com.siwol025.flight_monitor.notification.service.AlertConditionChecker;
import com.siwol025.flight_monitor.notification.mail.NotificationService;
import com.siwol025.flight_monitor.notification.worker.KafkaEmailTaskListener;
import com.siwol025.flight_monitor.notification.worker.KafkaNotificationListener;
import com.siwol025.flight_monitor.notification.worker.NotificationContentFormatter;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

class NotificationBeanRegistrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
            .withBean(SubscriptionService.class, () -> mock(SubscriptionService.class))
            .withBean(NotificationContentFormatter.class, () -> mock(NotificationContentFormatter.class))
            .withBean(AlertConditionChecker.class, () -> mock(AlertConditionChecker.class))
            .withBean(NotificationService.class, () -> mock(NotificationService.class))
            .withUserConfiguration(NotificationProducer.class, KafkaNotificationListener.class,
                    KafkaEmailTaskListener.class);

    @Test
    void 알림설정없이_카프카발행자와리스너빈_등록됨() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(NotificationProducer.class)
                .hasSingleBean(KafkaNotificationListener.class)
                .hasSingleBean(KafkaEmailTaskListener.class));
    }
}
