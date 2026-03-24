package com.siwol025.flight_monitor.monitor.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.monitor.utils.TaskQueueManager;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlightMonitoringProducer {

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;
    private final TaskQueueManager taskQueueManager;

    private static final String DISTRIBUTED_LOCK_KEY = "lock:flight:chunk:";
    private static final int MAX_QUEUE_THRESHOLD = 10000;
    private static final int CHUNK_SIZE = 1000;
    private static final long WAIT_TIME = 0L;
    private static final long LEASE_TIME = 60L;

    @Scheduled(fixedRate = 60000)
    public void produceMonitoringTasks() {
        Long currentQueueSize = taskQueueManager.getQueueSizeSafely();

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

            List<String> jsonPayloads = chunk.stream()
                    .map(this::toJson)
                    .toList();

            boolean shouldPublish = false;

            try {
                RLock lock = redissonClient.getLock(chunkLockKey);
                // 정상 상태: 락 획득 성공 시에만 발행 플래그 true
                if (lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS)) {
                    shouldPublish = true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // 장애 상태: Redis 예외 발생 시 서킷 브레이커 작동을 위해 강제 발행 플래그 true
                log.warn("🚨 [Producer] 분산 락 획득 실패(Redis 다운 의심). 중복 발행을 막기 위해 이번 주기를 스킵합니다. 원인: {}", e.getMessage());
            }

            // 플래그가 true일 경우 큐 매니저 호출 (정상 처리 또는 DB Fallback 트리거)
            if (shouldPublish) {
                taskQueueManager.publishTasks(jsonPayloads);
                log.info("✅ [Server:{}] Chunk {}-{} 투입 시도 완료", System.getProperty("server.id"), i, end);
            }
        }
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
