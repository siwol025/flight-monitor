package com.siwol025.flight_monitor.monitor.utils;

import com.siwol025.flight_monitor.global.fallback.domain.FallbackMonitoringTask;
import com.siwol025.flight_monitor.global.fallback.repository.FallbackMonitoringTaskRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskQueueManager {

    private final StringRedisTemplate redisTemplate;
    private final FallbackMonitoringTaskRepository fallbackRepository;

    private static final String TASK_QUEUE_KEY = "monitoring:task:queue";

    @CircuitBreaker(name = "redisQueue", fallbackMethod = "fallbackPublishTasks")
    public void publishTasks(List<String> jsonPayloads) {
        if (jsonPayloads == null || jsonPayloads.isEmpty()) {
            return;
        }
        redisTemplate.opsForList().leftPushAll(TASK_QUEUE_KEY, jsonPayloads);
    }

    private void fallbackPublishTasks(List<String> jsonPayloads, Throwable t) {
        log.warn("🚨 [CircuitBreaker] Redis 큐 발행 실패. DB 대기열로 전환합니다. 원인: {}", t.getMessage());

        List<FallbackMonitoringTask> fallbackTasks = jsonPayloads.stream()
                .map(p -> FallbackMonitoringTask.builder()
                        .payload(p)
                        .build())
                .toList();

        fallbackRepository.saveAll(fallbackTasks);
        log.info("✅ [Fallback] DB 임시 큐에 {}건의 작업 저장 완료", fallbackTasks.size());
    }

    public Long getQueueSizeSafely() {
        try {
            return redisTemplate.opsForList().size(TASK_QUEUE_KEY);
        } catch (Exception e) {
            log.error("⚠️ [Queue] Redis 큐 크기 조회 실패. DB 대체 큐가 작동 중일 수 있습니다.");
            return 0L;
        }
    }
}
