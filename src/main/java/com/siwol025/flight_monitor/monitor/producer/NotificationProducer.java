package com.siwol025.flight_monitor.monitor.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.mode", havingValue = "kafka", matchIfMissing = true)
public class NotificationProducer implements NotificationPublisher{

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_NAME = "flight-price-drop-events";

    @Override
    @Async("notificationEventExecutor")
    public void publishPriceDrop(PriceDropNotificationDto priceDropNotificationDto) {
        try {
            String messageKey = String.valueOf(priceDropNotificationDto.flightId());

            kafkaTemplate.send(TOPIC_NAME, messageKey, priceDropNotificationDto);
            log.info("🚀 [Kafka] 카프카 알림 발송 성공: 항공편 {}", priceDropNotificationDto.flightNumber());
        } catch (Exception e) {
            log.error("🚨 [Kafka] 카프카 알림 전송 실패 (메시지 유실): 항공편 {}", priceDropNotificationDto.flightNumber(), e);
        }
    }
}
