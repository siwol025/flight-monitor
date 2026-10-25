package com.siwol025.flight_monitor.notification.producer;

import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!mock")
public class NotificationProducer implements NotificationPublisher{

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_NAME = "flight-price-drop-events";

    @Override
    @Async("notificationEventExecutor")
    public void publishPriceDrop(PriceDropNotificationDto priceDropNotificationDto) {
        try {
            String messageKey = String.valueOf(priceDropNotificationDto.flightId());

            kafkaTemplate.send(TOPIC_NAME, messageKey, priceDropNotificationDto);
        } catch (Exception e) {
            log.error("[NotificationProducer] 카프카 알림 전송 실패 (메시지 유실): 항공편 {}", priceDropNotificationDto.flightNumber(), e);
        }
    }
}
