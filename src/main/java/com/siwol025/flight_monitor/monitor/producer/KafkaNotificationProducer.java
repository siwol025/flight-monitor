package com.siwol025.flight_monitor.monitor.producer;

import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.mode", havingValue = "kafka", matchIfMissing = true)
public class KafkaNotificationProducer implements NotificationPublisher{

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC_NAME = "flight-price-drop-events";

    @Override
    public void publishPriceDrop(PriceDropNotificationDto priceDropNotificationDto) {
        try {
            String messageKey = String.valueOf(priceDropNotificationDto.flightId());
            kafkaTemplate.send(TOPIC_NAME, messageKey, priceDropNotificationDto);

            log.info("🚀 [Kafka Producer] 토픽({})에 알림 발행 완료: 항공편 {}",
                    TOPIC_NAME, priceDropNotificationDto.flightNumber());
        } catch (Exception e) {
            log.error("🚨 [Kafka Producer] 알림 메시지 발행 실패: 항공편 {}",
                    priceDropNotificationDto.flightNumber(), e);
        }
    }
}
