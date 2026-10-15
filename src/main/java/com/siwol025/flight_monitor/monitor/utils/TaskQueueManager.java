package com.siwol025.flight_monitor.monitor.utils;

import com.siwol025.flight_monitor.global.fallback.service.FallbackTaskService;
import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
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
    private final FallbackTaskService fallbackTaskService;
    private final TaskQueueConsumerManager taskQueueConsumerManager;
    private final PipelineMetrics pipelineMetrics;

    private static final String TASK_QUEUE_KEY = "monitoring:task:queue";

    @CircuitBreaker(name = "redisPushCircuitBreaker", fallbackMethod = "fallbackPublishTasks")
    public void publishTasks(List<String> jsonPayloads) {
        if (jsonPayloads == null || jsonPayloads.isEmpty()) {
            return;
        }
        redisTemplate.opsForList().leftPushAll(TASK_QUEUE_KEY, jsonPayloads);
    }

    public void fallbackPublishTasks(List<String> jsonPayloads, Throwable t) {
        log.warn("[TaskQueueManager] Redis 큐 발행 실패. DB 대기열로 전환합니다. 원인: {}", t.getMessage());

        // 단일 발행자는 ShedLock이 보장하므로 폴백 저장에 별도 락을 두지 않는다(CLAUDE.md: 발행 로직에 분산락 금지).
        try {
            fallbackTaskService.savePendingTasks(jsonPayloads);
        } catch (Exception e) {
            // Redis·DB 이중 장애: 유실은 피할 수 없지만 조용히 사라지지 않게 남기고, 예외는 삼켜 남은 청크 발행을 계속한다.
            // 유실분은 다음 주기 프로듀서 재발행으로 회복된다.
            log.error("[TaskQueueManager] DB 폴백 저장 실패. 작업 {}건 유실.", jsonPayloads.size(), e);
            pipelineMetrics.recordDropped(jsonPayloads.size());
            return;
        }
        taskQueueConsumerManager.markFallbackTaskExists();
    }

    @CircuitBreaker(name = "redisPushCircuitBreaker", fallbackMethod = "fallbackGetQueueSizeSafely")
    public Long getQueueSizeSafely() {
        return redisTemplate.opsForList().size(TASK_QUEUE_KEY);
    }

    private Long fallbackGetQueueSizeSafely(Throwable t) {
        log.warn("[TaskQueueManager] 큐 사이즈 조회 실패. DB 기준 개수를 반환합니다.");
        return fallbackTaskService.countTasks();
    }
}
