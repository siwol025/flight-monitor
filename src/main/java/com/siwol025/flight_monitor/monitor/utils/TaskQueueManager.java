package com.siwol025.flight_monitor.monitor.utils;

import com.siwol025.flight_monitor.global.fallback.service.FallbackTaskService;
import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskQueueManager {

    private final StringRedisTemplate redisTemplate;
    private final FallbackTaskService fallbackTaskService;
    private final TaskQueueConsumerManager taskQueueConsumerManager;
    private final PipelineMetrics pipelineMetrics;
    private final Clock clock;

    // 감시 큐(ZSET). 멤버 = 노선 ID, 점수 = 처음 넣은 시각(epoch ms). 소비 쪽(TaskQueueConsumerManager)도 이 키를 쓴다.
    // 옛 리스트 키(monitoring:task:queue)와 이름이 달라야 같은 키에 리스트·ZSET 명령이 섞이지 않는다(WRONGTYPE)
    static final String ROUTE_QUEUE_KEY = "monitoring:route:queue";

    /**
     * ZADD NX로 넣는다. 이미 기다리는 노선은 무시되고 처음 넣은 시각을 지키므로 큐 길이는 활성 노선 수를 넘지 않는다.
     */
    @CircuitBreaker(name = "redisPushCircuitBreaker", fallbackMethod = "fallbackPublishTasks")
    public void publishTasks(List<String> jsonPayloads) {
        if (jsonPayloads == null || jsonPayloads.isEmpty()) {
            return;
        }
        // 점수는 발행자 시계다(ShedLock으로 주기당 발행자 하나라 주기 사이 순서만 맞으면 된다). 한계: 리더가 시계가 주기
        // 이상 늦은 서버로 바뀌면 그동안 새로 넣은 노선이 앞지를 수 있고(누락은 없음), 같은 청크는 점수가 같아 멤버 문자열 순이다
        double enqueuedAt = clock.millis();
        Set<TypedTuple<String>> routes = jsonPayloads.stream()
                .map(routeId -> TypedTuple.of(routeId, enqueuedAt))
                .collect(Collectors.toSet());
        redisTemplate.opsForZSet().addIfAbsent(ROUTE_QUEUE_KEY, routes);
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
        return redisTemplate.opsForZSet().zCard(ROUTE_QUEUE_KEY);
    }

    private Long fallbackGetQueueSizeSafely(Throwable t) {
        log.warn("[TaskQueueManager] 큐 사이즈 조회 실패. DB 기준 개수를 반환합니다.");
        return fallbackTaskService.countTasks();
    }
}
