package com.siwol025.flight_monitor.monitor.utils;

import com.siwol025.flight_monitor.global.fallback.repository.FallbackMonitoringTaskRepository;
import com.siwol025.flight_monitor.global.fallback.service.FallbackTaskService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskQueueConsumerManager {

    private final StringRedisTemplate redisTemplate;
    private final FallbackTaskService fallbackTaskService;

    private static final String TASK_QUEUE_KEY = "monitoring:task:queue";

    @CircuitBreaker(name = "redisQueue", fallbackMethod = "fallbackPopTask")
    public String popTask() {
        return redisTemplate.opsForList().rightPop(TASK_QUEUE_KEY, 1, TimeUnit.SECONDS);
    }

    public String fallbackPopTask(Throwable t) {
        log.warn("🚨 [Fallback] Redis 큐 다운. DB에서 안전하게 대체 작업을 가져옵니다.");
        return fallbackTaskService.processPendingTask();
    }
}
