package com.siwol025.flight_monitor.monitor.utils;

import com.siwol025.flight_monitor.global.fallback.service.FallbackTaskService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private final AtomicBoolean fallbackTaskExists = new AtomicBoolean(false);

    @CircuitBreaker(name = "redisPopCircuitBreaker", fallbackMethod = "recoverFromRedisPopFailure")
    public String blockAndPopTask(long timeoutSeconds) {
        return redisTemplate.opsForList().rightPop(TASK_QUEUE_KEY, Duration.ofSeconds(timeoutSeconds));
    }

    public String recoverFromRedisPopFailure(long timeoutSeconds, Throwable t) {
        log.warn("🚨 [Redis 장애] Redis Pop 실패. fallback queue 사용", t);
        fallbackTaskExists.set(true);
        String payload = pollFallbackQueue();

        if (payload == null) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return payload;
    }

    public String pollFallbackQueue() {
        String payload = fallbackTaskService.processPendingTask();
        if (payload == null) {
            fallbackTaskExists.set(
                    fallbackTaskService.hasPendingTask()
            );
        }

        return payload;
    }

    public boolean hasPendingFallbackTask() {
        return fallbackTaskExists.get();
    }

    public void markFallbackTaskExists() {
        fallbackTaskExists.set(true);
    }
}