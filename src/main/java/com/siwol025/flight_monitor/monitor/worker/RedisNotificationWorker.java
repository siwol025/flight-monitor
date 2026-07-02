package com.siwol025.flight_monitor.monitor.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.dto.UserEmailDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.mode", havingValue = "redis")
public class RedisNotificationWorker {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final SubscriptionService subscriptionService;
    private final NotificationContentFormatter formatter;

    private static final String QUEUE_NAME = "notification:queue";
    private static final String TASK_QUEUE = "email:task:queue";

    @Async("mailExecutor")
    @Scheduled(fixedDelay = 500)
    public void processNotification() {
        String json = redisTemplate.opsForList().rightPop(QUEUE_NAME);
        if (json == null) return;

        try {
            PriceDropNotificationDto eventDto = objectMapper.readValue(json,
                    PriceDropNotificationDto.class);

            log.info("📩 [Worker] 알림 처리 시작: 항공편 ID: {}", eventDto.flightNumber());

            List<UserEmailDto> subscribers = subscriptionService.getSubscribers(eventDto.flightId());

            String subject = formatter.createSubject(eventDto.flightNumber());
            String content = formatter.createContent(eventDto);

            for (UserEmailDto user : subscribers) {
                EmailSendTaskDto taskDto = new EmailSendTaskDto(user.email(), subject, content);
                String taskJson = objectMapper.writeValueAsString(taskDto);
                redisTemplate.opsForList().leftPush(TASK_QUEUE, taskJson);
            }

            log.info("✅항공편 {}, 생성된 발송 작업 {}건", eventDto.flightId(), subscribers.size());
        } catch (Exception e) {
            log.error("알림 Worker 처리 중 오류 발생", e);
        }
    }

}
