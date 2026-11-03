package com.siwol025.flight_monitor.notification.producer;

import com.siwol025.flight_monitor.global.config.RabbitMqConfig;
import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!mock")
public class NotificationProducer implements NotificationPublisher{

    private final RabbitTemplate rabbitTemplate;

    @Override
    @Async("notificationEventExecutor")
    public void publishPriceDrop(PriceDropNotificationDto priceDropNotificationDto) {
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.NOTIFICATION_EXCHANGE, RabbitMqConfig.PRICE_DROP_EVENTS_QUEUE,
                    priceDropNotificationDto);
        } catch (Exception e) {
            log.error("[NotificationProducer] 알림 이벤트 전송 실패 (메시지 유실): 노선 {}", priceDropNotificationDto.routeId(), e);
        }
    }
}
