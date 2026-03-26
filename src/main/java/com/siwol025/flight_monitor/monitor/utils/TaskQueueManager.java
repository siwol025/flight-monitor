package com.siwol025.flight_monitor.monitor.utils;

import com.siwol025.flight_monitor.global.fallback.domain.FallbackMonitoringTask;
import com.siwol025.flight_monitor.global.fallback.repository.FallbackMonitoringTaskRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskQueueManager {

    private final StringRedisTemplate redisTemplate;
    private final FallbackMonitoringTaskRepository fallbackRepository;
    private final RedissonClient redissonClient;

    private static final String TASK_QUEUE_KEY = "monitoring:task:queue";
    private static final String DB_LOCK_KEY = "db_lock:";
    private static final long WAIT_TIME = 0L;
    private static final long LEASE_TIME = 50L;

    @CircuitBreaker(name = "redisQueue", fallbackMethod = "fallbackPublishTasks")
    public void publishTasks(String chunkLockKey, List<String> jsonPayloads) {
        if (jsonPayloads == null || jsonPayloads.isEmpty()) {
            return;
        }
        try {
            RLock lock = redissonClient.getLock(chunkLockKey);

            if (lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS)) {
                log.info("✅ [Redis Lock] 락 획득 성공 (Key: {}). 레디스 큐에 작업을 저장합니다.", chunkLockKey);

                redisTemplate.opsForList().leftPushAll(TASK_QUEUE_KEY, jsonPayloads);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void fallbackPublishTasks(String chunkLockKey, List<String> jsonPayloads, Throwable t) {
        log.warn("🚨 [CircuitBreaker] Redis 큐 발행 실패. DB 대기열로 전환합니다. 원인: {}", t.getMessage());

        executeDbFallbackWithLock(chunkLockKey, jsonPayloads);
    }

    @Transactional
    public void executeDbFallbackWithLock(String chunkLockKey, List<String> jsonPayloads) {
        String dbLockKey = DB_LOCK_KEY + chunkLockKey;

        try {
            Integer lockResult = fallbackRepository.getLock(dbLockKey, 0);

            if (lockResult != null && lockResult == 1) {
                log.info("✅ [Fallback] DB 락 획득 성공 (Key: {}). DB 큐에 작업을 저장합니다.", dbLockKey);
                List<FallbackMonitoringTask> fallbackTasks = jsonPayloads.stream()
                        .map(p -> FallbackMonitoringTask.builder()
                                .payload(p)
                                .build()
                        ).toList();

                fallbackRepository.saveAll(fallbackTasks);
            }
        } finally {
            fallbackRepository.releaseLock(dbLockKey);
        }
    }

    @CircuitBreaker(name = "redisQueue", fallbackMethod = "fallbackGetQueueSizeSafely")
    public Long getQueueSizeSafely() {
        return redisTemplate.opsForList().size(TASK_QUEUE_KEY);
    }

    private Long fallbackGetQueueSizeSafely(Throwable t) {
        log.warn("🚨 [CircuitBreaker] 큐 사이즈 조회 실패. DB 기준 개수를 반환합니다.");
        return fallbackRepository.count();
    }
}
