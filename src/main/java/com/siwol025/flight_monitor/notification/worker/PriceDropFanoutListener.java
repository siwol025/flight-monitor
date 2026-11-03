package com.siwol025.flight_monitor.notification.worker;

import com.siwol025.flight_monitor.global.config.RabbitMqConfig;
import com.siwol025.flight_monitor.notification.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.notification.service.AlertConditionChecker;
import com.siwol025.flight_monitor.subscription.dto.SubscriberWithConditionDto;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!mock")
public class PriceDropFanoutListener {

    private final SubscriptionService subscriptionService;
    private final RabbitTemplate rabbitTemplate;
    private final NotificationContentFormatter formatter;
    private final AlertConditionChecker alertConditionChecker;

    @RabbitListener(queues = RabbitMqConfig.PRICE_DROP_EVENTS_QUEUE, concurrency = "3")
    public void processNotification(PriceDropNotificationDto eventDto) {
        try {
            List<SubscriberWithConditionDto> subscribers = subscriptionService.getSubscribersWithCondition(eventDto.routeId());

            if (subscribers.isEmpty()) {
                return;
            }

            String subject = formatter.createSubject(eventDto);
            String content = formatter.createContent(eventDto);

            for (SubscriberWithConditionDto subscriber : subscribers) {
                if (!alertConditionChecker.shouldNotify(eventDto.oldPrice(), eventDto.newPrice(), subscriber.targetPrice(), subscriber.dropThresholdPercent())) {
                    continue;
                }
                EmailSendTaskDto taskDto = new EmailSendTaskDto(subscriber.email(), subject, content);
                try {
                    rabbitTemplate.convertAndSend(RabbitMqConfig.NOTIFICATION_EXCHANGE,
                            RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE, taskDto);
                } catch (Exception e) {
                    log.error("[PriceDropFanoutListener] 특정 유저 이메일 작업 전송 실패: {}", subscriber.email(), e);
                }
            }
        } catch (Exception e) {
            log.error("[PriceDropFanoutListener] 알림 분배 처리 중 오류 발생", e);
        }
    }

}
