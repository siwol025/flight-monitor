package com.siwol025.flight_monitor.monitor.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.mode", havingValue = "redis")
public class RedisNotificationProducer implements NotificationPublisher{

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String QUEUE_NAME = "notification:queue";

    public void publishPriceDrop(PriceDropNotificationDto priceDropNotificationDto) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(priceDropNotificationDto);
            redisTemplate.opsForList().leftPush(QUEUE_NAME, jsonMessage);
            log.info("[Producer] Redis 큐에 알림 발행 완료: 항공편 {}", priceDropNotificationDto.flightNumber());
        } catch (JsonProcessingException e) {
            log.error("알림 메시지 JSON 변환 실패", e);
        }
    }
}
