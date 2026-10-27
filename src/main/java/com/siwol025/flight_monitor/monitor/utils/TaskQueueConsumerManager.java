package com.siwol025.flight_monitor.monitor.utils;

import com.siwol025.flight_monitor.global.fallback.service.FallbackTaskService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskQueueConsumerManager {

    private final StringRedisTemplate redisTemplate;
    private final FallbackTaskService fallbackTaskService;

    private final AtomicBoolean fallbackTaskExists = new AtomicBoolean(false);

    /**
     * BZPOPMIN으로 가장 오래 기다린 노선을 꺼낸다. 시간 안에 없으면 null.
     */
    @CircuitBreaker(name = "redisPopCircuitBreaker", fallbackMethod = "recoverFromRedisPopFailure")
    public String blockAndPopTask(long timeoutSeconds) {
        TypedTuple<String> route = redisTemplate.opsForZSet()
                .popMin(TaskQueueManager.ROUTE_QUEUE_KEY, Duration.ofSeconds(timeoutSeconds));
        return route == null ? null : route.getValue();
    }

    public String recoverFromRedisPopFailure(long timeoutSeconds, Throwable t) {
        log.warn("[TaskQueueConsumerManager] Redis Pop 실패. fallback 큐를 사용합니다.", t);
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