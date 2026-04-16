package com.siwol025.flight_monitor.monitor.utils;

import com.siwol025.flight_monitor.global.fallback.service.FallbackTaskService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskQueueConsumerManager {

    private final StringRedisTemplate redisTemplate;
    private final FallbackTaskService fallbackTaskService;

    private static final String TASK_QUEUE_KEY = "monitoring:task:queue";

    @CircuitBreaker(name = "redisQueueRead", fallbackMethod = "fallbackBlockAndPopTask")
    public String blockAndPopTask(long timeoutSeconds) {
        return redisTemplate.opsForList().rightPop(TASK_QUEUE_KEY, Duration.ofSeconds(timeoutSeconds));
    }

    public String fallbackBlockAndPopTask(long timeoutSeconds, Throwable t) {
        log.warn("🚨 [Redis 장애] blockAndPopTask 실패. DB에서 대체 작업을 가져옵니다.", t);
        String jsonPayload = fallbackTaskService.processPendingTask();

        if (jsonPayload == null) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return jsonPayload;
    }

    @CircuitBreaker(name = "redisQueueRead", fallbackMethod = "fallbackPopTasksBatch")
    public List<String> popTasksBatch(int count) {
        return redisTemplate.opsForList().rightPop(TASK_QUEUE_KEY, count);
    }

    public List<String> fallbackPopTasksBatch(int count, Throwable t) {
        log.warn("🚨 [Redis 장애] popTasksBatch 실패. DB에서 대체 작업(최대 {}건)을 가져옵니다.", count, t);

        List<String> payloads = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String jsonPayload = fallbackTaskService.processPendingTask();
            if (jsonPayload == null) {
                break;
            }
            payloads.add(jsonPayload);
        }

        if (payloads.isEmpty()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Collections.emptyList();
        }

        return payloads;
    }
}