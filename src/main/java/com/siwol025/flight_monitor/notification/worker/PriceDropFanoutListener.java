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
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Profile;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
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

    // 예외는 삼키지 않는다. priceDropFanoutListenerContainerFactory의 재시도가 이벤트 전체를 다시 처리하고, 소진되면 DLQ로 보낸다.
    // 재시도는 이 메서드를 다시 부르므로(advice chain 안쪽은 첫 시도만 거친다) 시도별 실패 로그는 여기서 남긴다.
    // 구독자 루프 중간에 실패해 재시도하면 앞 구독자에게는 메일 작업이 한 번 더 발행된다(중복 감수)
    @RabbitListener(queues = RabbitMqConfig.PRICE_DROP_EVENTS_QUEUE,
            containerFactory = "priceDropFanoutListenerContainerFactory")
    public void processNotification(@Payload PriceDropNotificationDto eventDto,
                                    @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        try {
            fanOut(eventDto);
        } catch (RuntimeException e) {
            log.warn("[PriceDropFanoutRetry] 팬아웃 실패: routeId={}, id={}, cause={}", eventDto.routeId(), messageId,
                    NestedExceptionUtils.getMostSpecificCause(e).getClass().getSimpleName());
            throw e;
        }
    }

    private void fanOut(PriceDropNotificationDto eventDto) {
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
            rabbitTemplate.convertAndSend(RabbitMqConfig.NOTIFICATION_EXCHANGE,
                    RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE, taskDto);
        }
    }

}
