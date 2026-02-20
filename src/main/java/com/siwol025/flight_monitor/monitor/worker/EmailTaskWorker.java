package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailTaskWorker {
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    private static final String TASK_QUEUE = "email:task:queue";

    @Async("mailExecutor")
    @Scheduled(fixedDelay = 100)
    public void processEmailTask() {
        String json = redisTemplate.opsForList().rightPop(TASK_QUEUE);
        if (json == null) return;

        try {
            EmailSendTaskDto taskDto = objectMapper.readValue(json, EmailSendTaskDto.class);
            notificationService.sendPriceDropNotification(taskDto);
        } catch (Exception e) {
            log.error("개별 이메일 발송 처리 중 예외 발생", e);
        }
    }
}
