package com.siwol025.flight_monitor.monitor.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlightMonitoringProducer {

    private final SubscriptionService subscriptionService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;

    private static final String TASK_QUEUE_KEY = "monitoring:task:queue";
    private static final String DISTRIBUTED_LOCK_KEY = "lock:flight:chunk:";
    private static final int MAX_QUEUE_THRESHOLD = 10000;
    private static final int CHUNK_SIZE = 1000;
    private static final long WAIT_TIME = 0L;
    private static final long LEASE_TIME = 60L;

    @Scheduled(fixedRate = 60000)
    public void produceMonitoringTasks() {
        Long currentQueueSize = redisTemplate.opsForList().size(TASK_QUEUE_KEY);

        if (currentQueueSize != null && currentQueueSize > MAX_QUEUE_THRESHOLD) {
            log.warn("⏳ [Skip] 큐 대기 작업 임계치 초과 (현재: {}건). 발행 중단", currentQueueSize);
            return;
        }

        List<FlightMonitorTaskDto> targets = subscriptionService.getActiveFlights();
        if (targets.isEmpty()) {
            return;
        }

        for (int i = 0; i < targets.size(); i += CHUNK_SIZE) {
            int end = Math.min(i+CHUNK_SIZE, targets.size());
            List<FlightMonitorTaskDto> chunk = targets.subList(i, end);

            String chunkLockKey = DISTRIBUTED_LOCK_KEY + i;
            RLock lock = redissonClient.getLock(chunkLockKey);

            try {
                if (lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS)) {
                    List<String> jsonPayloads = chunk.stream()
                            .map(this::toJson)
                            .toList();

                    redisTemplate.opsForList().leftPushAll(TASK_QUEUE_KEY, jsonPayloads);
                    log.info("✅ [Server:{}] Chunk {}-{} 투입 완료", System.getProperty("server.id"), i, end);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        List<String> jsonPayloads = targets.stream()
                .map(this::toJson)
                .toList();

        redisTemplate.opsForList().leftPushAll(TASK_QUEUE_KEY, jsonPayloads);

        log.info("=== [가격 모니터링 스케줄러 시작] 총 감시 대상 수: {}건 ===", targets.size());
    }

    private String toJson(FlightMonitorTaskDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("🚨 [Producer] JSON 직렬화 실패: {}", dto, e);
            throw new RuntimeException("작업 페이로드 직렬화 오류", e);
        }
    }
}
