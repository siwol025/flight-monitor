package com.siwol025.flight_monitor.monitor.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWorker {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String QUEUE_NAME = "notification:queue";

    @Async("mailExecutor")
    @Scheduled(fixedDelay = 500)
    public void processNotification() {
        String json = redisTemplate.opsForList().rightPop(QUEUE_NAME);
        if (json == null) return;

        try {
            PriceDropNotificationDto priceDropNotificationDto = objectMapper.readValue(json,
                    PriceDropNotificationDto.class);
            log.info("📩 [Worker] 알림 처리 시작: 항공편 ID: {}", priceDropNotificationDto.flightNumber());

            //Thread.sleep(3000);

            log.info("✅ [Worker] 알림 발송 완료! (항공편: {})", priceDropNotificationDto.flightNumber());
        } catch (Exception e) {
            log.error("알림 Worker 처리 중 오류 발생", e);
        }
    }
}
